package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.model.Vendor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 余额查询基类。
 * 封装 HTTP 调用逻辑，子类只需实现 supports() 和 parseResponse()。
 */
public abstract class BaseBalanceProvider implements BalanceProvider {
    protected static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    protected abstract String getEndpoint();
    protected abstract BalanceInfo parseResponse(Vendor vendor, JsonNode root) throws Exception;

    /**
     * 子类可覆盖以注入自定义 User-Agent（如 Kimi Code 要求 KimiCLI/1.6）。
     * 返回 null 表示不注入。
     */
    protected String getExtraUserAgent() {
        return null;
    }

    /**
     * 构建完整 URL。子类可覆盖以使用不同于 vendor.base_url 的域名。
     */
    protected String buildUrl(Vendor vendor) {
        return vendor.getBaseUrl().replaceAll("/+$", "") + getEndpoint();
    }

    /**
     * 获取认证 token。返回 null 表示无法查询，跳过。
     * 子类可覆盖：使用 api_key 的供应商直接返回 api_key，
     * 需要专用凭证的供应商返回 balance_credential（无则返回 null）。
     */
    protected String getAuthToken(Vendor vendor) {
        String cred = vendor.getBalanceCredential();
        if (cred != null && !cred.isEmpty()) return cred;
        return null;
    }

    @Override
    public BalanceInfo queryBalance(Vendor vendor) throws Exception {
        String token = getAuthToken(vendor);
        if (token == null || token.isEmpty()) {
            return null;
        }

        String url = buildUrl(vendor);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(10))
            .GET();

        String extraUA = getExtraUserAgent();
        if (extraUA != null) {
            reqBuilder.header("User-Agent", extraUA);
        }

        HttpRequest req = reqBuilder.build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        return parseResponse(vendor, root);
    }
}
