package com.oneapi.relay;

import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.Candidate;
import com.oneapi.model.RelayError;
import com.oneapi.model.RelayException;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Terminal executor: builds UpstreamClient.RelayRequest from Candidate + RelayRequest
 * and delegates to UpstreamClient.relay().
 */
public class BaseRelay implements RelayExecutor {
    private static final Logger log = LoggerFactory.getLogger(BaseRelay.class);
    private static final String DEFAULT_PATH = "/v1/chat/completions";

    private final UpstreamClient upstreamClient;

    public BaseRelay(UpstreamClient upstreamClient) {
        this.upstreamClient = upstreamClient;
    }

    @Override
    public Future<RelayResult> execute(Candidate c, RelayRequest req) {
        MultiMap extraHeaders = MultiMap.caseInsensitiveMultiMap();
        if (c.extraHeaders() != null && !c.extraHeaders().isEmpty()) {
            c.extraHeaders().forEach(extraHeaders::add);
        }

        UpstreamClient.RelayRequest upstreamReq = new UpstreamClient.RelayRequest(
            c.vendor().getBaseUrl(),
            c.vendor().getApiKey(),
            DEFAULT_PATH,
            "POST",
            req.rawBody(),
            extraHeaders
        );

        return upstreamClient.relay(upstreamReq)
            .compose(resp -> {
                String body = resp.bodyAsString();
                int status = resp.statusCode();
                if (status >= 200 && status < 300) {
                    return Future.succeededFuture(new RelayResult(
                        status,
                        body,
                        c.upstreamModel(),
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
