package com.oneapi.background.balance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.model.Vendor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/**
 * 火山引擎 Coding Plan 余额查询。
 * <p>
 * 认证方式：HMAC-SHA256 签名（V4）。
 * balanceCredential 格式：accessKey|secretKey
 * <p>
 * 调用两个 API：
 * 1. GetPersonalPlan - 查询套餐状态（Plan=CodingPlan）
 * 2. GetCodingPlanUsage - 查询用量百分比
 */
public class VolcengineBalanceProvider implements BalanceProvider {
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    private static final String HOST = "ark.cn-beijing.volcengineapi.com";
    private static final String SERVICE = "ark";
    private static final String REGION = "cn-beijing";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_ONLY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper;

    public VolcengineBalanceProvider(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean supports(Vendor vendor) {
        return vendor.getName() != null && vendor.getName().equalsIgnoreCase("VOLC");
    }

    @Override
    public BalanceInfo queryBalance(Vendor vendor) throws Exception {
        String cred = vendor.getBalanceCredential();
        if (cred == null || cred.isEmpty()) return null;

        String[] parts = cred.split("\\|", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("balanceCredential must be accessKey|secretKey");
        }
        String accessKey = parts[0];
        String secretKey = parts[1];

        // 1. 查询套餐状态
        JsonNode planResult = callApi(accessKey, secretKey, "GetPersonalPlan",
            "{\"Plan\":\"CodingPlan\"}");
        String status = planResult.path("Status").asText("Unknown");
        String planType = planResult.path("PlanType").asText("Unknown");
        String endTime = planResult.path("EndTime").asText("");

        // 2. 查询用量
        JsonNode usageResult = callApi(accessKey, secretKey, "GetCodingPlanUsage",
            "{\"ProjectName\":\"default\"}");
        String usageStatus = usageResult.path("Status").asText("Unknown");

        double sessionPercent = 0;
        double weeklyPercent = 0;
        double monthlyPercent = 0;
        long weeklyReset = 0;
        long monthlyReset = 0;

        JsonNode quotaUsage = usageResult.path("QuotaUsage");
        if (quotaUsage.isArray()) {
            for (JsonNode q : quotaUsage) {
                String level = q.path("Level").asText("");
                double percent = q.path("Percent").asDouble(0);
                long reset = q.path("ResetTimestamp").asLong(-1);
                switch (level) {
                    case "session" -> sessionPercent = percent;
                    case "weekly" -> { weeklyPercent = percent; weeklyReset = reset; }
                    case "monthly" -> { monthlyPercent = percent; monthlyReset = reset; }
                }
            }
        }

        boolean available = "Running".equals(status) || "Running".equals(usageStatus);
        String balance = String.format("月度%.1f%%", monthlyPercent);
        String detail = String.format(
            "plan=%s(%s), session=%.1f%%, weekly=%.1f%%, monthly=%.1f%%, endTime=%s",
            planType, status, sessionPercent, weeklyPercent, monthlyPercent, endTime);

        return new BalanceInfo(vendor.getId(), vendor.getName(), available,
            balance, "percent", detail);
    }

    private JsonNode callApi(String accessKey, String secretKey, String action, String body)
            throws Exception {
        Instant now = Instant.now();
        String dateStr = DATE_FMT.format(now);
        String dateOnly = DATE_ONLY_FMT.format(now);

        // 计算 body hash
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String bodyHash = sha256Hex(bodyBytes);

        // 构建规范请求
        String canonicalHeaders = "content-type:application/json; charset=utf-8\nhost:" + HOST + "\n" +
            "x-content-sha256:" + bodyHash + "\nx-date:" + dateStr + "\n";
        String signedHeaders = "content-type;host;x-content-sha256;x-date";
        String canonicalRequest = "POST\n/\nAction=" + action + "&Version=2024-01-01\n" +
            canonicalHeaders + "\n" + signedHeaders + "\n" + bodyHash;

        // 构建签名字符串
        String credentialScope = dateOnly + "/" + REGION + "/" + SERVICE + "/request";
        String stringToSign = "HMAC-SHA256\n" + dateStr + "\n" + credentialScope + "\n" +
            sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));

        // 计算签名
        byte[] signingKey = getSigningKey(secretKey, dateOnly, REGION, SERVICE);
        String signature = hmacSha256Hex(signingKey, stringToSign);

        // 构建 Authorization header
        String authorization = "HMAC-SHA256 Credential=" + accessKey + "/" + credentialScope +
            ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        String url = "https://" + HOST + "/?Action=" + action + "&Version=2024-01-01";
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Date", dateStr)
            .header("X-Content-Sha256", bodyHash)
            .header("Authorization", authorization)
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root = mapper.readTree(resp.body());
        JsonNode error = root.path("ResponseMetadata").path("Error");
        if (error.has("Code")) {
            throw new RuntimeException(error.get("Code").asText() + ": " +
                error.path("Message").asText(""));
        }
        return root.path("Result");
    }

    private static byte[] getSigningKey(String secretKey, String date, String region, String service)
            throws Exception {
        byte[] kDate = hmacSha256(secretKey.getBytes(StandardCharsets.UTF_8), date);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        return hmacSha256(kService, "request");
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String hmacSha256Hex(byte[] key, String data) throws Exception {
        return HexFormat.of().formatHex(hmacSha256(key, data));
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
    }
}
