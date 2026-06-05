package com.oneapi.relay;

import com.oneapi.handler.UpstreamClient;
import com.oneapi.model.Candidate;
import com.oneapi.model.RelayError;
import com.oneapi.model.RelayException;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

/**
 * Streaming gate: pipes directly for streaming requests,
 * delegates to the decorator chain for non-streaming.
 * <p>
 * Streaming skips the decorator chain entirely because
 * stream responses cannot be represented as Future&lt;RelayResult&gt;.
 */
public class StreamingAdapter implements RelayExecutor {
    private static final String DEFAULT_PATH = "/v1/chat/completions";
    private static final long STREAM_TIMEOUT_MS = 60_000; // 60s timeout

    private final RelayExecutor inner;
    private final UpstreamClient upstreamClient;
    private final Vertx vertx;

    public StreamingAdapter(RelayExecutor inner, UpstreamClient upstreamClient, Vertx vertx) {
        this.inner = inner;
        this.upstreamClient = upstreamClient;
        this.vertx = vertx;
    }

    @Override
    public Future<RelayResult> execute(Candidate c, RelayRequest req) {
        if (req.isStreaming() && req.sink() != null) {
            return relayStream(c, req);
        }
        return inner.execute(c, req);
    }

    private Future<RelayResult> relayStream(Candidate c, RelayRequest req) {
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
            extraHeaders,
            req.sink()
        );

        Promise<RelayResult> promise = Promise.promise();
        long timerId = vertx.setTimer(STREAM_TIMEOUT_MS, tid -> {
            if (!promise.future().isComplete()) {
                promise.fail(new RelayException(
                    new RelayError.UpstreamFailure(504, "stream timeout")));
            }
        });
        upstreamClient.relayStream(upstreamReq, (status, tokens) -> {
            vertx.cancelTimer(timerId);
            if (status >= 200 && status < 300) {
                promise.complete(new RelayResult(status, "", c.upstreamModel(), 0, tokens));
            } else {
                promise.fail(
                    new RelayException(new RelayError.UpstreamFailure(status, "stream failed")));
            }
        });
        return promise.future();
    }
}
