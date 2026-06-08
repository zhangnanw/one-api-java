package com.oneapi.repo;

import java.util.List;

/** Model capability lookup — used by {@code CapabilityInstanceFilter}. */
public interface CapabilityCatalog {
    boolean hasCapability(String modelName, String capability);
    List<String> getCapabilities(String modelName);
}
