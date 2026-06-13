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
import java.util.function.Function;

/**
 * 上游客户端 — 负责将请求转发到供应商 API。
 * <p>
 * 两个实现：
 * - relay()：缓冲模式，等完整响应后再返回。支持重试，用于非流式请求。
 * - relayStream()：流式模式，边收边发。用于 SSE 流式响应。
 * <p>
 * WebClient 用于缓冲模式（自动处理重定向、重试友好）。
 * HttpClient 用于流式模式（真正的 chunk 级管道，不走缓冲）。
 */
public class UpstreamClient {
    private static final Logger log = LoggerFactory.getLogger(UpstreamClient.class);

    private final WebClient client;
    private final HttpClient rawClient;

    public UpstreamClient(WebClient client, Vertx vertx) {
        this.client = client;
        this.rawClient = vertx.createHttpClient(new HttpClientOptions()
            .setConnectTimeout(30000)
            .setIdleTimeout(120)
            .setKeepAlive(false));
    }

    /** 缓冲式中继 — 等待完整响应后返回。用于非流式请求，支持重试。 */
    public Future<HttpResponse<Buffer>> relay(OutboundRequest req) {
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
     * 流式中继 — 将上游的 SSE chunk 实时 pipe 到客户端。
     *
     * @param req            出站请求（含上游 URL、API Key、body）
     * @param onStatus       状态回调：返回 true 表示状态码可接受（继续 pipe），false 表示需要 fallback
     * @param onComplete     完成回调：(statusCode, totalTokens)
     * @param chunkConverter 可选的 chunk 转换器（如用于模型名称替换），返回转换后的 SSE 文本
     */
    public void relayStream(OutboundRequest req,
                            Function<Integer, Boolean> onStatus,
                            BiConsumer<Integer, Integer> onComplete,
                            Function<Buffer, String> chunkConverter) {
        String url = buildUrl(req.baseUrl, req.requestPath);
        HttpServerResponse sink = req.sink;

        var headers = MultiMap.caseInsensitiveMultiMap();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", "Bearer " + req.apiKey);
        if (req.extraHeaders != null) {
            for (var entry : req.extraHeaders) {
                headers.set(entry.getKey(), entry.getValue());
            }
        }

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
                .setSsl("https".equals(uri.getScheme()))
                .setTimeout(300000); // 5分钟超时，避免上游挂起

            rawClient.request(opts)
                .onSuccess(request -> {
                    headers.forEach(h -> request.putHeader(h.getKey(), h.getValue()));
                    request.send(Buffer.buffer(req.body))
                        .onSuccess(upstream -> {
                            int statusCode = upstream.statusCode();
                            
                            // 非 200 → 不 pipe 到客户端，让调用方 fallback
                            if (onStatus != null && !onStatus.apply(statusCode)) {
                                upstream.body().onSuccess(body -> {
                                    onComplete.accept(statusCode, parseTokensFromBody(body.toString()));
                                }).onFailure(err -> {
                                    onComplete.accept(statusCode, 0);
                                });
                                return;
                            }
                            
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
                                if (chunkConverter != null) {
                                    String converted = chunkConverter.apply(chunk);
                                    if (!converted.isEmpty()) {
                                        sink.write(Buffer.buffer(converted));
                                    }
                                } else {
                                    sink.write(chunk);
                                }
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

    /** 从单个 SSE chunk 中解析 total_tokens（避免完整 split）。 */
    private static void parseTokensFromChunk(Buffer chunk, int[] holder) {
        int t = parseTokensFromBody(chunk.toString());
        if (t > 0) holder[0] = t;
    }

    /** 逐行扫描 body 中的 total_tokens（避免一次性 split 成数组）。 */
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
                int versionCut = path.indexOf('/', 1);
                if (versionCut >= 0) path = path.substring(versionCut);
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

    // --- OutboundRequest ---

    /**
     * 出站传输：实际发往上游供应商的 HTTP 请求。
     * <p>
     * Outbound transport: the actual HTTP request sent to the upstream vendor.
     * <p>
     * Boundary: this is the transport-level record. It carries the upstream URL,
     * credentials, method, body and optional sink for streaming. The domain-level
     * inbound request is {@link com.oneapi.model.RelayRequest}.
     */
    public record OutboundRequest(
        String baseUrl,
        String apiKey,
        String requestPath,
        String method,
        byte[] body,
        MultiMap extraHeaders,
        HttpServerResponse sink
    ) {
        public OutboundRequest(String baseUrl, String apiKey, String requestPath,
                               String method, byte[] body, MultiMap extraHeaders) {
            this(baseUrl, apiKey, requestPath, method, body, extraHeaders, null);
        }
    }
}
