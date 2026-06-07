package com.oneapi.scoring;

import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import java.util.List;

/**
 * 可插拔的实例评分器。
 * 
 * 实现类根据候选实例的历史表现和会话上下文，
 * 重新排序候选列表。返回空列表表示使用默认排序（ByPref）。
 * 
 * 通过配置开关 scoring.enabled 控制是否启用。
 */
public interface InstanceScorer {

    /**
     * 对候选实例列表重新排序。
     *
     * @param candidates 当前候选列表（已通过 Filter 链筛选）
     * @param relayCtx   请求上下文（含会话信息、模型名等）
     * @return 重新排序后的列表；空列表 = 用默认排序
     */
    List<RoutedVendor> score(List<RoutedVendor> candidates, RelayContext relayCtx);
}
