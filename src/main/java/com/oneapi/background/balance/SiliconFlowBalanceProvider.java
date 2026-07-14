package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * SiliconFlow 余额查询。
 * GET /v1/user/info → {"data": {"balance": "100.00", "totalBalance": "200.00"}}
 */
public class SiliconFlowBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("SF");
    }

    @Override
    protected String getEndpoint() {
        return "/v1/user/info";
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        JsonNode data = root.get("data");
        if (data == null) {
            throw new RuntimeException("missing 'data' in response");
        }
        String balance = "0";
        if (data.has("balance")) {
            balance = data.get("balance").asText("0");
        }
        String totalBalance = balance;
        if (data.has("totalBalance")) {
            totalBalance = data.get("totalBalance").asText(balance);
        }
        double bal = 0;
        try { bal = Double.parseDouble(balance); } catch (NumberFormatException ignore) {}
        boolean available = bal > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, totalBalance, "CNY", root.toString());
    }
}
