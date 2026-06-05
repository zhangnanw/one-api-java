package com.oneapi.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.function.BiConsumer;

/**
 * Terminal handler: forwards request to upstream vendor.
 * Two backends:
 * - WebClient for buffered relay (retry-friendly)
 * - HttpClient for streaming relay (real pipe)
 */
public class UpstreamClient {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);

    private final WebClient client;
    private final HttpClient rawClient;

    public UpstreamClient(WebClient client, Vertx vertx) {
        this.client = client;
        this.rawClient = vertx.createHttpClient(new HttpClientOptions()
            .setConnectTimeout(10000)
            .setIdleTimeout(30)
            .setKeepAlive(false));
    }

    /** Buffered relay — returns full response body. Used for retry. */
    public Future<HttpResponse<Buffer>> relay(RelayRequest req) {
        String url = buildUrl(req.baseUrl, req.requestPath);

        var headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer " + req.apiKey);
        if (req.extraHeaders != null) headers.addAll(req.extraHeaders);

        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(req.method);
        } catch (IllegalArgumentException e) {
            log.warn("bad method: {}", req.method);
            return Future.failedFuture("bad method: " + req.method);
        }
        return client.requestAbs(httpMethod, url)
            .putHeaders(headers)
            .sendBuffer(Buffer.buffer(req.body))
            .onSuccess(resp -> log.debug("relay OK: {} -> status={}", url, resp.statusCode()))
            .onFailure(err -> log.error("relay failed: {}", err.getMessage()));
    }

    /**
     * Streaming relay — pipes upstream chunks directly to client.
     * @param onComplete callback: (statusCode, totalTokens) — called once after pipe ends.
     */
    public void relayStream(RelayRequest req, BiConsumer<Integer, Integer> onComplete) {
        String url = buildUrl(req.baseUrl, req.requestPath);
        HttpServerResponse sink = req.sink;

        var headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer " + req.apiKey);
        if (req.extraHeaders != null) headers.addAll(req.extraHeaders);

        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(req.method);
        } catch (IllegalArgumentException e) {
            log.warn("bad method: {}", req.method);
            if (!sink.ended())
                sink.setStatusCode(400).end("{\"error\":{\"message\":\"bad method\"}}");
            onComplete.accept(400, 0);
            return;
        }

        try {
            var uri = new URI(url);
            var opts = new RequestOptions()
                .setMethod(httpMethod)
                .setHost(uri.getHost())
                .setPort(uri.getPort() > 0 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80))
                .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                .setSsl("https".equals(uri.getScheme()));

            rawClient.request(opts)
                .onSuccess(request -> {
                    headers.forEach(h -> request.putHeader(h.getKey(), h.getValue()));
                    request.send(Buffer.buffer(req.body))
                        .onSuccess(upstream -> {
                            int statusCode = upstream.statusCode();
                            sink.setStatusCode(statusCode);
                            upstream.headers().forEach(h -> sink.putHeader(h.getKey(), h.getValue()));
                            if (sink.headers().get("Content-Type") == null) {
                                sink.putHeader("Content-Type", "text/event-stream");
                            }
                            sink.setChunked(true);

                            // Parse tokens from each SSE chunk on the fly (no unbounded accumulation)
                            final int[] tokensHolder = {0};
                            upstream.handler(chunk -> {
                                parseTokensFromChunk(chunk, tokensHolder);
                                sink.write(chunk);
                                if (sink.writeQueueFull()) {
                                    upstream.pause();
                                    sink.drainHandler(v2 -> upstream.resume());
                                }
                            });
                            upstream.endHandler(v -> {
                                sink.end();
                                log.debug("stream done: status={} tokens={}", statusCode, tokensHolder[0]);
                                onComplete.accept(statusCode, tokensHolder[0]);
                            });
                            upstream.resume();
                        })
                        .onFailure(err -> {
                            log.warn("stream relay: upstream unreachable: {}", err.getMessage());
                            if (!sink.ended())
                                sink.setStatusCode(502).end("{\"error\":{\"message\":\"upstream unreachable\"}}");
                            onComplete.accept(502, 0);
                        });
                })
                .onFailure(err -> {
                    log.warn("stream relay: request failed: {}", err.getMessage());
                    if (!sink.ended())
                        sink.setStatusCode(502).end("{\"error\":{\"message\":\"request failed\"}}");
                    onComplete.accept(502, 0);
                });
        } catch (Exception e) {
            log.error("stream relay: bad URL {}: {}", url, e.getMessage());
            if (!sink.ended()) sink.setStatusCode(502)
                .end("{\"error\":{\"message\":\"bad URL\"}}");
            onComplete.accept(502, 0);
        }
    }

    /** Parse total_tokens from accumulated SSE chunks. Returns 0 if not found. */
    private static int parseSSETokens(Buffer buf) {
        return parseTokensFromBody(buf.toString());
    }

    /** Parse tokens from a single chunk, updating holder with latest non-zero value. */
    private static void parseTokensFromChunk(Buffer chunk, int[] holder) {
        int t = parseTokensFromBody(chunk.toString());
        if (t > 0) holder[0] = t;
    }

    /** Scan body line-by-line for total_tokens (avoids full split into array). */
    private static int parseTokensFromBody(String body) {
        int totalTokens = 0;
        int start = 0;
        while (true) {
            int end = body.indexOf('\n', start);
            if (end < 0) break;
            String line = body.substring(start, end).trim();
            start = end + 1;
            if (line.startsWith("data: ") && !line.equals("data: [DONE]")) {
                try {
                    String json = line.substring(6);
                    JsonObject obj = new JsonObject(json);
                    JsonObject usage = obj.getJsonObject("usage");
                    if (usage != null) {
                        int t = usage.getInteger("total_tokens", 0);
                        if (t > 0) totalTokens = t; // take the last non-zero value
                    }
                } catch (Exception ignore) {}
            }
        }
        return totalTokens;
    }

    // --- URL building (matching Go) ---

    private String buildUrl(String base, String path) {
        if (path == null) path = "";

        int idx = base.lastIndexOf('/');
        if (idx >= 0) {
            String baseVer = base.substring(idx + 1);
            String prefix = "/" + baseVer;
            if (path.startsWith(prefix)) {
                path = path.substring(prefix.length());
            } else if (isApiVersionEnding(baseVer) && isApiVersionPath(path)) {
                int ndx = path.indexOf('/', 1);
                if (ndx >= 0) path = path.substring(ndx);
            }
        }
        return base + path;
    }

    private static boolean isApiVersionEnding(String v) {
        return v.length() >= 2 && v.charAt(0) == 'v' && Character.isDigit(v.charAt(1));
    }

    private static boolean isApiVersionPath(String p) {
        return p.length() >= 3 && p.charAt(0) == '/' && p.charAt(1) == 'v' && Character.isDigit(p.charAt(2));
    }

    // --- RelayRequest ---

    public record RelayRequest(
        String baseUrl,
        String apiKey,
        String requestPath,
        String method,
        byte[] body,
        MultiMap extraHeaders,
        HttpServerResponse sink
    ) {
        public RelayRequest(String baseUrl, String apiKey, String requestPath,
                            String method, byte[] body, MultiMap extraHeaders) {
            this(baseUrl, apiKey, requestPath, method, body, extraHeaders, null);
        }
    }
}
