package com.oneapi.model;

/**
 * 集中管理 meta JSON 字段名、标签前缀等跨模块共享的字符串常量。
 * 避免在过滤器、repo、service 各自硬编码键名导致拼写漂移。
 */
public final class MetaKeys {
    private MetaKeys() {}

    // meta JSON 字段
    public static final String LAYER = "layer";
    public static final String TAGS = "tags";
    public static final String NOCOOL = "nocool";
    public static final String PREF = "pref";
    public static final String MAX_TOKENS = "max_tokens";

    // 标签前缀
    public static final String CAPABILITY_PREFIX = "capability:";

    // 常用具体标签
    public static final String CAPABILITY_REASONING = CAPABILITY_PREFIX + "reasoning";
    public static final String CAPABILITY_VISION = CAPABILITY_PREFIX + "vision";
    public static final String CAPABILITY_TOOLS = CAPABILITY_PREFIX + "tools";
}
