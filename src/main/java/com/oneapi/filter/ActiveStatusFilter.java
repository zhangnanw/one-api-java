package com.oneapi.filter;

import com.oneapi.model.RelayContext;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 闃舵 3 鈥?浠呬繚鐣欐椿璺冨疄渚嬶紙STATUS_RAW 鎴?STATUS_TAGGED锛夛紝鎺掗櫎 DISABLED 鍜?DEPRECATED銆?
 */
public class ActiveStatusFilter implements Filter {
    private static final Logger log = LoggerFactory.getLogger(ActiveStatusFilter.class);

    @Override
    public RelayContext apply(RelayContext ctx) {
        List<RoutedVendor> candidates = ctx.candidates();
        if (candidates == null || candidates.isEmpty()) {
            return ctx;
        }

        List<RoutedVendor> filtered = candidates.stream()
            .filter(routedVendor -> routedVendor.instanceStatus() == InstanceRepo.STATUS_RAW
                       || routedVendor.instanceStatus() == InstanceRepo.STATUS_TAGGED)
            .toList();

        log.debug("ActiveStatusFilter: {} 鈫?{} candidates",
            candidates.size(), filtered.size());
        ctx.setCandidates(filtered);
        return ctx;
    }
}
