package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * Moonshot (Kimi) 余额查询。
 * GET /v1/users/me/balance → {"data": {"available_balance": 100.00}}
 */
public class MoonshotBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("KC");
    }

    @Override
    protected String getEndpoint() {
        return "/v1/users/me/balance";
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        JsonNode data = root.get("data");
        if (data == null) {
            throw new RuntimeException("missing 'data' in response");
        }
        double balance = data.has("available_balance") ? data.get("available_balance").asDouble(0) : 0;
        boolean available = balance > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, String.valueOf(balance), "CNY", root.toString());
    }
}
