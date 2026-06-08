package com.oneapi.relay;

import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.Candidate;
import com.oneapi.model.RelayError;
import com.oneapi.model.RelayException;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default terminal executor: builds an {@link UpstreamClient.OutboundRequest} from a
 * {@link Candidate} + {@link RelayRequest} and delegates to {@link UpstreamClient#relay}.
 */
public class DefaultRelay implements RelayExecutor {
    private static final Logger log = LoggerFactory.getLogger(DefaultRelay.class);
    private static final String DEFAULT_PATH = "/v1/chat/completions";

    private final UpstreamClient upstreamClient;

    public DefaultRelay(UpstreamClient upstreamClient) {
        this.upstreamClient = upstreamClient;
    }

    @Override
    public Future<RelayResult> execute(Candidate candidate, RelayRequest req) {
        if (candidate.vendor() == null) {
            return Future.failedFuture(new RelayException(
                new RelayError.UpstreamFailure(503, "vendor is null")));
        }

        UpstreamClient.OutboundRequest upstreamReq = new UpstreamClient.OutboundRequest(
            candidate.vendor().getBaseUrl(),
            candidate.vendor().getApiKey(),
            DEFAULT_PATH,
            "POST",
            req.rawBody(),
            candidate.extraHeaders()
        );

        return upstreamClient.relay(upstreamReq)
            .compose(resp -> {
                String body = resp.bodyAsString();
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    // 空响应检测：上游返回 200 但 choices 无效 → 视为失败，重试下一个实例
                    if (body == null || !hasChoices(body)) {
                        log.warn("upstream returned {} with null/empty choices: {}",
                            status, body != null ? body.substring(0, Math.min(200, body.length())) : "null");
                        return Future.failedFuture(
                            new RelayException(new RelayError.UpstreamFailure(status, body)));
                    }
                    return Future.succeededFuture(new RelayResult(
                        status,
                        body,
                        candidate.upstreamModel(),
                        parsePromptTokens(body),
                        parseCompletionTokens(body)
                    ));
                }
                log.warn("upstream returned {}: {}", status,
                    body.length() > 200 ? body.substring(0, 200) : body);
                return Future.failedFuture(
                    new RelayException(new RelayError.UpstreamFailure(status, body)));
            })
            .recover(err -> {
                if (err instanceof RelayException) {
                    return Future.failedFuture(err);  // already wrapped, pass through
                }
                log.error("relay failed: {}", err.getMessage());
                return Future.failedFuture(
                    new RelayException(new RelayError.UpstreamFailure(502, err.getMessage())));
            });
    }

    /** Quick check: does the body contain a non-null, non-empty "choices" array? */
    private static boolean hasChoices(String body) {
        if (body == null || body.isEmpty()) return false;
        // Check for "choices":null or "choices":[]
        int idx = body.indexOf("\"choices\"");
        if (idx < 0) return false;
        // Get the value after "choices":
        int colon = body.indexOf(':', idx);
        if (colon < 0) return false;
        String afterColon = body.substring(colon + 1).trim();
        return !afterColon.startsWith("null") && !afterColon.startsWith("[]");
    }

    private static int parsePromptTokens(String body) {
        try {
            JsonObject json = new JsonObject(body);
            JsonObject usage = json.getJsonObject("usage");
            return usage != null ? usage.getInteger("prompt_tokens", 0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseCompletionTokens(String body) {
        try {
            JsonObject json = new JsonObject(body);
            JsonObject usage = json.getJsonObject("usage");
            return usage != null ? usage.getInteger("completion_tokens", 0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
