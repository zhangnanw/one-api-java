package com.oneapi.relay;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;

/**
 * 当请求的模型名称以配置的触发后缀（默认："-max"）结尾时，
 * 注入 X-Reasoning-Effort 请求头。
 */
public class ReasoningDecorator implements RelayExecutor {
    private final RelayExecutor inner;
    private final String triggerSuffix;

    public ReasoningDecorator(RelayExecutor inner, String triggerSuffix) {
        this.inner = inner;
        this.triggerSuffix = triggerSuffix;
    }

    @Override
    public Future<RelayResult> execute(Candidate c, RelayRequest req) {
        if (req.requestedModel() != null && req.requestedModel().endsWith(triggerSuffix)) {
            c.extraHeaders().put("X-Reasoning-Effort", "max");
        }
        return inner.execute(c, req);
    }
}
