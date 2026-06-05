package com.oneapi.filter;

import com.oneapi.model.RelayContext;

/**
 * 实例/模型选择管道的单一职责过滤器。
 */
@FunctionalInterface
public interface Filter {
    RelayContext apply(RelayContext ctx);
    // 通过则原样返回 ctx；失败则设置 ctx.error。
}
