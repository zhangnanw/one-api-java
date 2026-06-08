package com.oneapi.repo;

/** Model context window lookup — used by {@code BodyLimitFilter}. */
public interface WindowCatalog {
    int getContextWindow(String modelName);
}
