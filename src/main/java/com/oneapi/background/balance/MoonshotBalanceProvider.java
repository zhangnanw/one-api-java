package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * Kimi Code / Moonshot 余额查询。
 * Kimi Code CLI /usage 命令实际调用：
 *   GET https://api.kimi.com/coding/v1/usages
 *   Authorization: Bearer <sk-kimi-xxx API Key>
 * 响应包含 7 天周期配额和 5 小时滚动窗口配额。
 */
public class MoonshotBalanceProvider extends BaseBalanceProvider {

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("KC");
    }

    @Override
    protected String getAuthToken(Vendor vendor) {
        return vendor.getApiKey();
    }

    @Override
    protected String getEndpoint() {
        return "/coding/v1/usages";
    }

    @Override
    protected String buildUrl(Vendor vendor) {
        // Kimi Code 的 usages 端点使用独立域名
        return "https://api.kimi.com" + getEndpoint();
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        JsonNode usage = root.get("usage");
        if (usage == null) {
            throw new RuntimeException("missing 'usage' in response");
        }
        double remaining = 0;
        double limit = 0;
        if (usage.has("remaining")) {
            remaining = Double.parseDouble(usage.get("remaining").asText("0"));
        }
        if (usage.has("limit")) {
            limit = Double.parseDouble(usage.get("limit").asText("0"));
        }
        boolean available = remaining > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available,
            String.valueOf(remaining), "requests",
            "limit=" + limit + ", remaining=" + remaining);
    }
}
