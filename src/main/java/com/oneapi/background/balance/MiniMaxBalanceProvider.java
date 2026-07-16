package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.model.Vendor;

/**
 * MiniMax 余额查询。
 *
 * balanceCredential 格式：
 *   token-plan:<subscription-key>  — 查 Token Plan（/v1/token_plan/remains）
 *   coding-plan:<subscription-key> — 查 Coding Plan（/v1/api/openplatform/coding_plan/remains）
 *   <subscription-key>             — 默认查 Token Plan
 */
public class MiniMaxBalanceProvider extends BaseBalanceProvider {

    public MiniMaxBalanceProvider(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("MiniMax");
    }

    private boolean isCodingPlan(Vendor vendor) {
        String cred = vendor.getBalanceCredential();
        return cred != null && cred.startsWith("coding-plan:");
    }

    private String extractKey(Vendor vendor) {
        String cred = vendor.getBalanceCredential();
        if (cred == null || cred.isEmpty()) return null;
        if (cred.startsWith("token-plan:")) return cred.substring("token-plan:".length());
        if (cred.startsWith("coding-plan:")) return cred.substring("coding-plan:".length());
        return cred;
    }

    @Override
    protected String getEndpoint() {
        return ""; // buildUrl 覆盖
    }

    @Override
    protected String getAuthToken(Vendor vendor) {
        return extractKey(vendor);
    }

    @Override
    protected String buildUrl(Vendor vendor) {
        String path = isCodingPlan(vendor)
            ? "/v1/api/openplatform/coding_plan/remains"
            : "/v1/token_plan/remains";
        return "https://www.minimaxi.com" + path;
    }

    @Override
    protected BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception {
        if (isCodingPlan(vendor)) {
            return parseCodingPlan(vendor, root);
        }
        return parseTokenPlan(vendor, root);
    }

    private BalanceInfo parseTokenPlan(Vendor vendor, JsonNode root) {
        JsonNode remains = root.get("model_remains");
        if (remains == null || !remains.isArray() || remains.isEmpty()) {
            return new BalanceInfo(vendor.getId(), vendor.getName(), false, "0", "N/A", root.toString());
        }
        long totalRemaining = 0;
        for (JsonNode item : remains) {
            long total = item.path("current_interval_total_count").asLong(0);
            long used = item.path("current_interval_usage_count").asLong(0);
            totalRemaining += (total - used);
        }
        return new BalanceInfo(vendor.getId(), vendor.getName(), totalRemaining > 0,
            String.valueOf(totalRemaining), "tokens", root.toString());
    }

    private BalanceInfo parseCodingPlan(Vendor vendor, JsonNode root) {
        // Coding Plan 响应格式同 Token Plan: {"model_remains": [...]}
        // 每个模型有 current_interval_total_count / current_interval_usage_count
        JsonNode remains = root.get("model_remains");
        if (remains == null || !remains.isArray() || remains.isEmpty()) {
            return new BalanceInfo(vendor.getId(), vendor.getName(), false, "0", "N/A", root.toString());
        }
        StringBuilder detail = new StringBuilder();
        long totalRemaining = 0;
        for (JsonNode item : remains) {
            String model = item.path("model_name").asText("unknown");
            long total = item.path("current_interval_total_count").asLong(0);
            long used = item.path("current_interval_usage_count").asLong(0);
            long remaining = total - used;
            totalRemaining += remaining;
            if (total > 0) {
                int pct = (int) (used * 100 / total);
                detail.append(model).append(":").append(pct).append("% ");
            }
        }
        return new BalanceInfo(vendor.getId(), vendor.getName(), totalRemaining > 0,
            String.valueOf(totalRemaining), "tokens", detail.toString().trim());
    }
}
