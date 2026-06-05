package com.oneapi.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

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
        this.rawClient = vertx.createHttpClient();
    }

    /** Buffered relay — returns full response body. Used for retry. */
    public Future<HttpResponse<Buffer>> relay(RelayRequest req) {
        String url = buildUrl(req.baseUrl, req.requestPath);

        var headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer " + req.apiKey);
        if (req.extraHeaders != null) headers.addAll(req.extraHeaders);

        return client.requestAbs(HttpMethod.valueOf(req.method), url)
            .putHeaders(headers)
            .sendBuffer(Buffer.buffer(req.body))
            .onSuccess(resp -> log.debug("relay OK: {} → status={}", url, resp.statusCode()))
            .onFailure(err -> log.error("relay failed: {}", err.getMessage()));
    }

    /** Streaming relay — pipes upstream chunks directly to client. */
    public void relayStream(RelayRequest req) {
        String url = buildUrl(req.baseUrl, req.requestPath);
        HttpServerResponse sink = req.sink;

        var headers = MultiMap.caseInsensitiveMultiMap();
        headers.add("Content-Type", "application/json");
        headers.add("Authorization", "Bearer " + req.apiKey);
        if (req.extraHeaders != null) headers.addAll(req.extraHeaders);

        try {
            var uri = new URI(url);
            var opts = new RequestOptions()
                .setMethod(HttpMethod.valueOf(req.method))
                .setHost(uri.getHost())
                .setPort(uri.getPort() > 0 ? uri.getPort() : (uri.getScheme().equals("https") ? 443 : 80))
                .setURI(uri.getPath() + (uri.getQuery() != null ? "?" + uri.getQuery() : ""))
                .setSsl("https".equals(uri.getScheme()));

            rawClient.request(opts)
                .onSuccess(request -> {
                    headers.forEach(h -> request.putHeader(h.getKey(), h.getValue()));
                    request.send(Buffer.buffer(req.body))
                        .onSuccess(upstream -> {
                            sink.setStatusCode(upstream.statusCode());
                            upstream.headers().forEach(h -> sink.putHeader(h.getKey(), h.getValue()));
                            if (sink.headers().get("Content-Type") == null) {
                                sink.putHeader("Content-Type", "text/event-stream");
                            }
                            sink.setChunked(true);
                            upstream.pipeTo(sink)
                                .onFailure(err2 -> {
                                    log.error("pipe broke: {}", err2.getMessage());
                                    if (!sink.ended()) sink.end();
                                });
                        })
                        .onFailure(err -> {
                            log.warn("stream relay: upstream unreachable: {}", err.getMessage());
                            if (!sink.ended())
                                sink.setStatusCode(502).end("{\"error\":{\"message\":\"upstream unreachable\"}}");
                        });
                })
                .onFailure(err -> {
                    log.warn("stream relay: request failed: {}", err.getMessage());
                    if (!sink.ended())
                        sink.setStatusCode(502).end("{\"error\":{\"message\":\"request failed\"}}");
                });
        } catch (Exception e) {
            log.error("stream relay: bad URL {}: {}", url, e.getMessage());
            if (!sink.ended()) sink.setStatusCode(502)
                .end("{\"error\":{\"message\":\"bad URL\"}}");
        }
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
