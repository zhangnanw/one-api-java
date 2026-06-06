package com.oneapi.relay;

import com.oneapi.model.Candidate;
import com.oneapi.model.RelayRequest;
import com.oneapi.model.RelayResult;
import io.vertx.core.Future;

/**
 * 针对候选项执行转发请求。
 * 装饰器模式 —— 每个实现包装或扩展另一个实现。
 */
public interface RelayExecutor {
    Future<RelayResult> execute(Candidate candidate, RelayRequest req);
}
