package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.util.TestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * NameMatcher 单元测试。
 *
 * 核心行为：物理模型名（出现在 instances 表）不设任何 ctx 字段，
 * 交给后续 VirtualModelLookup filter 决定。
 * API 表面只暴露虚拟模型，不暴露具体实例。
 */
@ExtendWith(MockitoExtension.class)
class NameMatcherTest {

    @Mock
    InstanceRepo instanceRepo;

    /** 物理模型名（已登记到 instances 表，但不在 virtual_models 表） */
    private static final String PHYSICAL_MODEL = "doubao-seed-2.0-pro";

    @Test
    void strict_skipsMatchedPhysicalMark() {
        when(instanceRepo.existsByModelName(PHYSICAL_MODEL)).thenReturn(true);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceRepo).apply(ctx);

        // 严格模式：filter 不 set 任何字段
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.upstreamModel()).isNull();
    }

    @Test
    void unknownModel_skipsMatchedPhysical() {
        when(instanceRepo.existsByModelName(PHYSICAL_MODEL)).thenReturn(false);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceRepo).apply(ctx);

        // 物理实例表里也没有 → filter 不动 ctx，留给后续 filter
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.upstreamModel()).isNull();
    }

    @Test
    void emptyModel_earlyReturn() {
        // 注意：不 mock existsByModelName —— emptyModel 应早 return，不调用 repo
        RelayContext ctx = TestFixtures.relayContext(null, null, "");
        new NameMatcher(instanceRepo).apply(ctx);

        // 早 return：ctx 默认 state，filter 没动
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.upstreamModel()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
    }
}
