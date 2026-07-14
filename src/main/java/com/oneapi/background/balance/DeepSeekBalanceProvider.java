package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * DeepSeek 余额查询。
 * GET /user/balance → {"is_available": true, "balance_infos": [{"total_balance": "100.00"}]}
 */
public class DeepSeekBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("DS");
    }

    @Override
    protected String getEndpoint() {
        return "/user/balance";
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        boolean available = root.has("is_available") && root.get("is_available").asBoolean(false);
        String total = "0";
        JsonNode infos = root.get("balance_infos");
        if (infos != null && infos.isArray() && !infos.isEmpty()) {
            JsonNode first = infos.get(0);
            if (first.has("total_balance")) {
                total = first.get("total_balance").asText("0");
            }
        }
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, total, "CNY", root.toString());
    }
}
