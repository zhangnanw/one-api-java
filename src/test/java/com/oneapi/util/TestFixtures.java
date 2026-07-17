package com.oneapi.util;

import com.oneapi.model.MatchRule;
import com.oneapi.model.RelayContext;
import com.oneapi.entity.Vendor;
import com.oneapi.entity.Instance;
import com.oneapi.core.RouterService.RoutedVendor;

import java.util.List;

/**
 * 测试 fixture 工厂。
 * 集中构造 Vendor / Instance / RoutedVendor / RelayContext，
 * 避免每个测试写一遍样板。
 */
public final class TestFixtures {

    private TestFixtures() {}

    /** 构造一个带 id / name 的最小 Vendor，其他字段留空。 */
    public static Vendor vendor(int id, String name) {
        Vendor v = new Vendor();
        v.setId(id);
        v.setName(name);
        return v;
    }

    /** 构造一个带 id / modelName / meta 的最小 Instance（无 vendor）。 */
    public static Instance instance(int id, String modelName, String metaJson) {
        Instance inst = new Instance();
        inst.setId(id);
        inst.setModelName(modelName);
        inst.setMeta(metaJson);
        return inst;
    }

    /**
     * 构造一个最小 RoutedVendor。
     * instanceTags 是逗号分隔 String（与 CooldownService 接口签名一致）。
     * instanceMeta 是 JSON 字符串（用于 MetaView.fromInstanceMeta 解析）。
     * modelName 与 upstreamModel 默认相同。
     */
    public static RoutedVendor routedVendor(int instanceId,
                                             String instanceTags,
                                             String instanceMeta,
                                             Vendor vendor) {
        return new RoutedVendor(
            vendor, "model", "model", instanceId, instanceTags, instanceMeta, 1
        , 0f, "payg");
    }

    /**
     * 构造一个带候选列表与规则的 RelayContext。
     * requestedModel 默认取 "kimi-k2.6"（in-db 的虚拟模型名）。
     */
    public static RelayContext relayContext(List<RoutedVendor> candidates,
                                             MatchRule rule,
                                             String requestedModel) {
        RelayContext ctx = new RelayContext(requestedModel);
        if (candidates != null) {
            ctx.setCandidates(candidates);
        }
        if (rule != null) {
            ctx.setMatchRule(rule);
        }
        return ctx;
    }
}
