package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * OpenRouter 余额查询。
 * GET /api/v1/credits → {"data": {"total_credits": 100.0, "total_usage": 50.0}}
 */
public class OpenRouterBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("OpenRouter");
    }

    @Override
    protected String getEndpoint() {
        return "/api/v1/credits";
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        JsonNode data = root.get("data");
        if (data == null) {
            throw new RuntimeException("missing 'data' in response");
        }
        double totalCredits = data.has("total_credits") ? data.get("total_credits").asDouble(0) : 0;
        double totalUsage = data.has("total_usage") ? data.get("total_usage").asDouble(0) : 0;
        double remaining = totalCredits - totalUsage;
        boolean available = remaining > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, String.valueOf(remaining), "USD", root.toString());
    }
}
