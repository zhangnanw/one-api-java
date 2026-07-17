package com.oneapi.relay;

import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.Candidate;
import com.oneapi.model.RelayError;
import com.oneapi.model.RelayException;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Default terminal executor: builds an {@link UpstreamClient.OutboundRequest} from a
 * {@link Candidate} + {@link RelayRequest} and delegates to {@link UpstreamClient#relay}.
 */
@Slf4j
@RequiredArgsConstructor
public class DefaultRelay implements RelayExecutor {
    private static final String DEFAULT_PATH = "/v1/chat/completions";

    private final UpstreamClient upstreamClient;

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
                    if (body == null) {
                        log.warn("upstream returned {} with null body", status);
                        return Future.failedFuture(
                            new RelayException(new RelayError.UpstreamFailure(status, null)));
                    }
                    // Parse body once as JsonObject
                    JsonObject json;
                    try {
                        json = new JsonObject(body);
                    } catch (Exception e) {
                        log.warn("upstream returned {} with invalid JSON: {}",
                            status, body.substring(0, Math.min(200, body.length())));
                        return Future.failedFuture(
                            new RelayException(new RelayError.UpstreamFailure(status, body)));
                    }
                    // Check choices validity from parsed object
                    if (!hasChoices(json)) {
                        log.warn("upstream returned {} with null/empty choices: {}",
                            status, body.substring(0, Math.min(200, body.length())));
                        return Future.failedFuture(
                            new RelayException(new RelayError.UpstreamFailure(status, body)));
                    }
                    return Future.succeededFuture(new RelayResult(
                        status,
                        body,
                        candidate.upstreamModel(),
                        parsePromptTokens(json),
                        parseCompletionTokens(json)
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

    /** Check: does the parsed JsonObject contain a non-null, non-empty "choices" array? */
    private static boolean hasChoices(JsonObject json) {
        if (json == null) return false;
        var choices = json.getJsonArray("choices");
        return choices != null && !choices.isEmpty();
    }

    private static int parsePromptTokens(JsonObject json) {
        JsonObject usage = json.getJsonObject("usage");
        return usage != null ? usage.getInteger("prompt_tokens", 0) : 0;
    }

    private static int parseCompletionTokens(JsonObject json) {
        JsonObject usage = json.getJsonObject("usage");
        return usage != null ? usage.getInteger("completion_tokens", 0) : 0;
    }
}
