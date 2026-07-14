package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * MiniMax 余额查询。
 * GET https://www.minimaxi.com/v1/token_plan/remains
 * 只能查 Token Plan（订阅套餐）额度。
 */
public class MiniMaxBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("MiniMax");
    }

    @Override
    protected String getEndpoint() {
        return "/v1/token_plan/remains";
    }

    @Override
    protected String getAuthToken(Vendor vendor) {
        String cred = vendor.getBalanceCredential();
        if (cred != null && !cred.isEmpty()) return cred;
        // Token Plan 专用 Key，api_key（按量付费）不能用
        return null;
    }

    @Override
    protected String buildUrl(Vendor vendor) {
        // MiniMax 余额接口域名和 API 调用域名不同
        return "https://www.minimaxi.com" + getEndpoint();
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        // 响应格式: {"model_remains": [{"model_name": "...", "current_interval_total_count": 600, "current_interval_usage_count": 42}]}
        JsonNode remains = root.get("model_remains");
        if (remains == null || !remains.isArray() || remains.isEmpty()) {
            return new BalanceInfo(vendor.getId(), vendor.getName(), false, "0", "N/A", root.toString());
        }
        long totalRemaining = 0;
        long totalCount = 0;
        for (JsonNode item : remains) {
            long total = item.has("current_interval_total_count") ? item.get("current_interval_total_count").asLong(0) : 0;
            long used = item.has("current_interval_usage_count") ? item.get("current_interval_usage_count").asLong(0) : 0;
            totalCount += total;
            totalRemaining += (total - used);
        }
        boolean available = totalRemaining > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, String.valueOf(totalRemaining), "tokens", root.toString());
    }
}
