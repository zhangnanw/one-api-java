package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.model.RelayError;
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
 * 核心行为：
 * - 请求模型名 == 物理模型名（出现在 instances 表）→ 设 matchedPhysical = true，
 *   标记 DirectUseForbidden 错误（404），明确提示"只接受虚拟模型名"
 * - 没命中 → 透传 ctx
 */
@ExtendWith(MockitoExtension.class)
class NameMatcherTest {

    @Mock
    InstanceRepo instanceRepo;

    /** 物理模型名（已登记到 instances 表，但不在 virtual_models 表） */
    private static final String PHYSICAL_MODEL = "doubao-seed-2.0-pro";

    @Test
    void physicalModelName_markedAsMatchedPhysical_withError() {
        when(instanceRepo.existsByModelName(PHYSICAL_MODEL)).thenReturn(true);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceRepo).apply(ctx);

        // 新行为：设 matchedPhysical、mark DirectUseForbidden（404）
        assertThat(ctx.matchedPhysical()).isTrue();
        assertThat(ctx.hasError()).isTrue();
        assertThat(ctx.error()).isInstanceOf(RelayError.DirectUseForbidden.class);
        assertThat(ctx.errorMessage()).contains("physical model");
        assertThat(ctx.errorMessage()).contains("virtual model");
        // 其他旧字段（matchRule、routingModelName）保持不变（即 null）
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
    }

    @Test
    void unknownModel_skipsMatchedPhysical() {
        when(instanceRepo.existsByModelName(PHYSICAL_MODEL)).thenReturn(false);

        RelayContext ctx = TestFixtures.relayContext(null, null, PHYSICAL_MODEL);
        new NameMatcher(instanceRepo).apply(ctx);

        // 物理实例表里也没有 → filter 不动 ctx，留给后续 filter
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
        assertThat(ctx.hasError()).isFalse();
    }

    @Test
    void emptyModel_earlyReturn() {
        // 注意：不 mock existsByModelName —— emptyModel 应早 return，不调用 repo
        RelayContext ctx = TestFixtures.relayContext(null, null, "");
        new NameMatcher(instanceRepo).apply(ctx);

        // 早 return：ctx 默认 state，filter 没动
        assertThat(ctx.matchRule()).isNull();
        assertThat(ctx.routingModelName()).isNull();
        assertThat(ctx.matchedPhysical()).isFalse();
        assertThat(ctx.hasError()).isFalse();
    }
}
