package com.oneapi.scoring;

import com.oneapi.model.RelayContext;
import com.oneapi.service.RouterService.RoutedVendor;
import java.util.List;

/**
 * 空操作评分器。始终返回空列表，让系统使用默认排序（ByPref）。
 * 当 scoring.enabled=false 时使用此实现。
 */
public class NoopInstanceScorer implements InstanceScorer {

    @Override
    public List<RoutedVendor> score(List<RoutedVendor> candidates, RelayContext relayCtx) {
        return List.of();
    }
}
