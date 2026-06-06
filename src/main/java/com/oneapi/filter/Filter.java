package com.oneapi.filter;

import com.oneapi.model.RelayContext;

/**
 * 实例/模型选择管道的单一职责过滤器。
 * <p>
 * 通过则原样返回 ctx；失败则设置 ctx.error。
 */
@FunctionalInterface
public interface Filter {
    RelayContext apply(RelayContext ctx);

    /**
     * 链式组合：先应用当前 filter，再应用 {@code after}。
     * <p>
     * 用于在 RouterConfig 拼装阶段把多个轻量 filter 串成一条。
     * 注意：{@code after} 仍会执行，即使当前 filter 已标记错误；
     * 短路语义由调用方在协调器层判断（ctx.hasError）。
     */
    default Filter andThen(Filter after) {
        return ctx -> {
            RelayContext after1 = this.apply(ctx);
            return after.apply(after1);
        };
    }
}
