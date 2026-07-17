package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.entity.Vendor;

/**
 * OpenRouter 余额查询。
 * GET https://openrouter.ai/api/v1/auth/key
 * Response: {"data": {"label":"...", "limit":50.0, "limit_remaining":30.0, "usage":20.0, ...}}
 */
public class OpenRouterBalanceProvider extends BaseBalanceProvider {

    public OpenRouterBalanceProvider(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("OpenRouter");
    }

    @Override
    protected String getAuthToken(Vendor vendor) {
        return vendor.getApiKey();
    }

    @Override
    protected String buildUrl(Vendor vendor) {
        return "https://openrouter.ai/api/v1/auth/key";
    }

    @Override
    protected String getEndpoint() {
        return ""; // buildUrl 覆盖了完整路径
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        JsonNode data = root.get("data");
        if (data == null) {
            throw new RuntimeException("missing 'data' in response");
        }
        // limit: 信用额度上限，null表示无限制
        // limit_remaining: 剩余额度
        // usage: 累计已用
        double limit = data.has("limit") && !data.get("limit").isNull() ? data.get("limit").asDouble(0) : -1;
        double limitRemaining = data.has("limit_remaining") && !data.get("limit_remaining").isNull() ? data.get("limit_remaining").asDouble(0) : -1;
        double usage = data.path("usage").asDouble(0);
        boolean isFreeTier = data.path("is_free_tier").asBoolean(false);

        boolean available = limitRemaining != 0; // -1 表示无限制也视为可用
        String balance = limitRemaining >= 0 ? String.valueOf(limitRemaining) : "unlimited";
        String detail = String.format("limit=%s, remaining=%s, usage=%.4f, free_tier=%s",
            limit >= 0 ? String.valueOf(limit) : "unlimited",
            balance, usage, isFreeTier);

        return new BalanceInfo(vendor.getId(), vendor.getName(), available,
            balance, "USD", detail);
    }
}
