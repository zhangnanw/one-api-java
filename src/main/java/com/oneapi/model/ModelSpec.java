package com.oneapi.model;

import java.util.Set;

/**
 * 模型事实库条目。表示某个逻辑模型名（如 deepseek-v4-pro）的规范属性。
 * 不依赖具体 vendor，多个 vendor 提供同一模型时共享同一 ModelSpec。
 */
public record ModelSpec(
    String name,
    Set<String> capabilities,
    int contextWindow,
    double inputRmbPerM,
    double outputRmbPerM
) {}
