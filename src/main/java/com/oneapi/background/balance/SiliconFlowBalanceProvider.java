package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.model.Vendor;

/**
 * SiliconFlow 余额查询。
 * GET /v1/user/info
 * balance=赠送余额, chargeBalance=充值余额, totalBalance=总余额
 */
public class SiliconFlowBalanceProvider extends BaseBalanceProvider {

    public SiliconFlowBalanceProvider(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("SF");
    }

    @Override
    protected String getAuthToken(Vendor vendor) {
        return vendor.getApiKey();
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
        String grantBalance = data.path("balance").asText("0");
        String chargeBalance = data.path("chargeBalance").asText("0");
        String totalBalance = data.path("totalBalance").asText("0");
        String status = data.path("status").asText("");

        // 如果 status 是 "normal"，就认为可用（不管 totalBalance 正负）
        boolean available = "normal".equalsIgnoreCase(status);

        String detail = String.format("grant=%s, charged=%s, total=%s, status=%s", grantBalance, chargeBalance, totalBalance, status);
        return new BalanceInfo(vendor.getId(), vendor.getName(), available, totalBalance, "CNY", detail);
    }
}
