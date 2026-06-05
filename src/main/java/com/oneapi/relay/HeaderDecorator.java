package com.oneapi.relay;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;

/**
 * Injects vendor-specific headers. Currently only the kimi.com UA hack.
 */
public class HeaderDecorator implements RelayExecutor {
    private final RelayExecutor inner;

    public HeaderDecorator(RelayExecutor inner) {
        this.inner = inner;
    }

    @Override
    public Future<RelayResult> execute(Candidate c, RelayRequest req) {
        String baseUrl = c.vendor().getBaseUrl();
        if (baseUrl != null && baseUrl.contains("kimi.com")) {
            // HACK: kimi API requires CLI UA for access
            c.extraHeaders().put("User-Agent", "KimiCLI/1.6");
        }
        return inner.execute(c, req);
    }
}
