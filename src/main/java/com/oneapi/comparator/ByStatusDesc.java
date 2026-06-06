package com.oneapi.comparator;

import com.oneapi.service.RouterService.RoutedVendor;

import java.util.Comparator;

/**
 * 第四阶段 — 已标注实例（STATUS_TAGGED）排在原始实例（STATUS_RAW）之前。
 */
public class ByStatusDesc implements Comparator<RoutedVendor> {
    @Override
    public int compare(RoutedVendor a, RoutedVendor b) {
        // STATUS_TAGGED (2) > STATUS_RAW (1)：状态值越大优先级越高
        // 反转：已标注(2)在前，原始(1)在后
        return Integer.compare(b.instanceStatus(), a.instanceStatus());
    }
}
