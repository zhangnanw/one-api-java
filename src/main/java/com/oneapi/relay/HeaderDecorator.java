package com.oneapi.relay;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;

/**
 * 注入厂商特定的请求头。目前仅包含 kimi.com 的 UA 处理。
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
            // HACK：kimi API 需要 CLI UA 才能访问
            c.extraHeaders().put("User-Agent", "KimiCLI/1.6");
        }
        return inner.execute(c, req);
    }
}
