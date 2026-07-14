package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.oneapi.model.Vendor;

/**
 * Kimi Code 余额查询。
 * <p>
 * Kimi Code CLI /usage 命令实际调用：
 *   GET https://api.kimi.com/coding/v1/usages
 *   Authorization: Bearer sk-kimi-xxx
 * <p>
 * 响应包含长周期配额（7天）和短期窗口配额（5分钟）。
 * <p>
 * 注意：这与 Moonshot 开放平台（按量付费，/v1/users/me/balance）是不同的产品。
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
        return "https://api.kimi.com" + getEndpoint();
    }

    @Override
    protected String getExtraUserAgent() {
        return "KimiCLI/1.6";
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        // 长周期配额（7天）
        JsonNode usage = root.path("usage");
        int limit = usage.path("limit").asInt(0);
        int used = usage.path("used").asInt(0);
        int remaining = usage.path("remaining").asInt(0);
        String resetTime = usage.path("resetTime").asText("");

        // 短期窗口配额（5分钟）
        int shortRemaining = 0;
        JsonNode limits = root.path("limits");
        if (limits.isArray() && !limits.isEmpty()) {
            shortRemaining = limits.get(0).path("detail").path("remaining").asInt(0);
        }

        // 总配额
        JsonNode totalQuota = root.path("totalQuota");
        int totalRemaining = totalQuota.path("remaining").asInt(0);
        int totalLimit = totalQuota.path("limit").asInt(0);

        boolean available = remaining > 0;
        String balance = remaining + "/" + limit;
        String detail = String.format(
            "total=%d/%d, 7d: used=%d, remaining=%d/%d, reset=%s, 5min_remaining=%d",
            totalRemaining, totalLimit,
            used, remaining, limit, resetTime, shortRemaining);

        return new BalanceInfo(vendor.getId(), vendor.getName(), available,
            balance, "requests", detail);
    }
}
