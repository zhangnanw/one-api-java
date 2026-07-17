package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.entity.Vendor;

/**
 * 小米 MiMo 余额查询（非官方 API）。
 * GET https://token-plan-cn.xiaomimimo.com/v1/token_plan/remains
 * 该端点来自第三方 CLI 工具逆向，非官方文档化，可能随时变更。
 */
public class MimoBalanceProvider extends BaseBalanceProvider {

    public MimoBalanceProvider(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().contains("imo");
    }

    @Override
    protected String getEndpoint() {
        return "/v1/token_plan/remains";
    }

    @Override
    protected String buildUrl(Vendor vendor) {
        // MiMo Token Plan 专用域名
        return "https://token-plan-cn.xiaomimimo.com" + getEndpoint();
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        // 响应格式: {"data": [{"model_name": "...", "remaining": 123, "total": 456}]}
        JsonNode data = root.get("data");
        if (data == null || !data.isArray() || data.isEmpty()) {
            return new BalanceInfo(vendor.getId(), vendor.getName(), false, "0", "N/A", root.toString());
        }
        long totalRemaining = 0;
        for (JsonNode item : data) {
            if (item.has("remaining")) {
                totalRemaining += item.get("remaining").asLong(0);
            }
        }
        boolean available = totalRemaining > 0;
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, String.valueOf(totalRemaining), "tokens", root.toString());
    }
}
