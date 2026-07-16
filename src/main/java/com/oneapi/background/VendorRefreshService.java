package com.oneapi.background;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oneapi.jpa.InstanceJpaRepository;
import com.oneapi.jpa.VendorJpaRepository;
import com.oneapi.model.Instance;
import com.oneapi.model.MetaKeys;
import com.oneapi.model.Vendor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Auto-refresh vendor models by calling GET /models on each vendor.
 * Matches Go's model/vendor.go RefreshAllVendorModels().
 */
@Service
public class VendorRefreshService {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final VendorJpaRepository vendorRepo;
    private final InstanceJpaRepository instanceRepo;
    private final ObjectMapper mapper;

    public VendorRefreshService(InstanceJpaRepository instanceRepo, VendorJpaRepository vendorRepo,
                                ObjectMapper mapper) {
        this.instanceRepo = instanceRepo;
        this.vendorRepo = vendorRepo;
        this.mapper = mapper;
    }

    public record RefreshResult(int created, int deprecated, List<String> errors) {}

    public RefreshResult refreshAll() {
        if (!LOCK.tryLock()) {
            return new RefreshResult(0, 0, List.of("refresh already in progress"));
        }
        try {
            int created = 0, deprecated = 0;
            List<String> errors = new ArrayList<>();

            for (Vendor vendor : vendorRepo.findByStatus(1)) {
                if (vendor.getBaseUrl() == null || vendor.getBaseUrl().isEmpty()
                    || vendor.getApiKey() == null || vendor.getApiKey().isEmpty()) {
                    continue;
                }
                try {
                    var result = refreshOne(vendor);
                    created += result.created;
                    deprecated += result.deprecated;
                    errors.addAll(result.errors);
                } catch (Exception e) {
                    errors.add(vendor.getName() + ": " + e.getMessage());
                }
            }
            return new RefreshResult(created, deprecated, errors);
        } finally {
            LOCK.unlock();
        }
    }

    private RefreshResult refreshOne(Vendor vendor) throws Exception {
        String url = vendor.getBaseUrl();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/v1/models"))
            .header("Authorization", "Bearer " + vendor.getApiKey())
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            return new RefreshResult(0, 0, List.of(vendor.getName() + " returned " + resp.statusCode()));
        }
        JsonNode root = mapper.readTree(resp.body());
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return new RefreshResult(0, 0, List.of(vendor.getName() + " response missing data array"));
        }

        Set<String> liveModels = new HashSet<>();
        int created = 0, deprecated = 0;
        List<String> errors = new ArrayList<>();
        for (JsonNode node : data) {
            String modelName = node.path("id").asText(null);
            if (modelName == null || modelName.isEmpty()) continue;
            liveModels.add(modelName);
            if (!instanceRepo.existsByModelNameAndStatusIn(modelName,
                    java.util.List.of(Instance.STATUS_RAW, Instance.STATUS_TAGGED))) {
                Instance inst = new Instance();
                inst.setVendorId(vendor.getId());
                inst.setModelName(modelName);
                inst.setUpstreamModel(modelName);
                inst.setStatus(Instance.STATUS_RAW);
                inst.setMeta("{}");
                inst.setLayer(detectLayer(modelName));
                inst.setPref(0.5f);
                inst.setCreatedTime(System.currentTimeMillis() / 1000);
                instanceRepo.save(inst);
                created++;
            }
        }

        // Mark missing instances as deprecated
        List<Instance> existing = instanceRepo.findByVendorId(vendor.getId());
        for (Instance inst : existing) {
            if (!liveModels.contains(inst.getModelName()) && inst.getStatus() == Instance.STATUS_RAW) {
                inst.setStatus(Instance.STATUS_DEPRECATED);
                instanceRepo.save(inst);
                deprecated++;
            }
        }
        return new RefreshResult(created, deprecated, errors);
    }

    private String detectLayer(String modelName) {
        String lower = modelName.toLowerCase();
        if (lower.contains("free") || lower.contains("trial")) return "free";
        if (lower.contains("sub") || lower.contains("subscription")) return "subscription";
        return "payg";
    }
}
