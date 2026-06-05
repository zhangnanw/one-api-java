package com.oneapi.sort;

import com.oneapi.repo.InstanceRepo;
import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 第四阶段 — 将原始实例（STATUS_RAW=1）排在已标注实例（STATUS_TAGGED=2）之后。
 * 状态值越小排序越靠后，从而降低原始实例的优先级。
 */
public class RawStatusLast implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        // STATUS_TAGGED (2) > STATUS_RAW (1)：状态值越大优先级越高
        // 反转：已标注(2)在前，原始(1)在后
        return Integer.compare(b.instanceStatus(), a.instanceStatus());
    }
}
