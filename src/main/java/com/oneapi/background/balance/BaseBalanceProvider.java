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

    protected String getAuthToken(Vendor vendor) {
        String cred = vendor.getBalanceCredential();
        if (cred != null && !cred.isEmpty()) return cred;
        return vendor.getApiKey();
    }

    @Override
    public BalanceInfo queryBalance(Vendor vendor) throws Exception {
        String token = getAuthToken(vendor);
        if (token == null || token.isEmpty()) {
            throw new IllegalStateException("no credential for vendor " + vendor.getName());
        }

        String url = vendor.getBaseUrl().replaceAll("/+$", "") + getEndpoint();
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Bearer " + token)
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        return parseResponse(vendor, root);
    }
}
