package com.oneapi.service;

import com.oneapi.model.Vendor;
import com.oneapi.model.Instance;
import com.oneapi.repo.VendorRepo;
import com.oneapi.repo.InstanceRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
public class VendorRefreshService {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ReentrantLock lock = new ReentrantLock();
    private static final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private final VendorRepo vendorRepo = new VendorRepo();
    private final InstanceRepo instanceRepo = new InstanceRepo();

    public record RefreshResult(int created, int deprecated, List<String> errors) {}

    public RefreshResult refreshAll() {
        if (!lock.tryLock()) {
            return new RefreshResult(0, 0, List.of("refresh already in progress"));
        }
        try {
            int created = 0, deprecated = 0;
            List<String> errors = new ArrayList<>();

            for (Vendor v : vendorRepo.findAllActive()) {
                if (v.getBaseUrl() == null || v.getBaseUrl().isEmpty()
                    || v.getApiKey() == null || v.getApiKey().isEmpty()) {
                    continue;
                }
                try {
                    var r = refreshOne(v);
                    created += r.created;
                    deprecated += r.deprecated;
                    errors.addAll(r.errors);
                } catch (Exception e) {
                    errors.add("vendor " + v.getName() + ": " + e.getMessage());
                }
            }
            return new RefreshResult(created, deprecated, errors);
        } finally {
            lock.unlock();
        }
    }

    record OneResult(int created, int deprecated, List<String> errors) {}

    private OneResult refreshOne(Vendor v) {
        List<String> errors = new ArrayList<>();
        String url = v.getBaseUrl().replaceAll("/+$", "") + "/models";

        try {
            var req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + v.getApiKey())
                .header("User-Agent", "one-api-refresh/1.0")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                errors.add("vendor " + v.getName() + ": " + url + " returned " + resp.statusCode());
                return new OneResult(0, 0, errors);
            }

            JsonNode root = mapper.readTree(resp.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                errors.add("vendor " + v.getName() + ": invalid models response");
                return new OneResult(0, 0, errors);
            }

            // Build response model set
            Set<String> respModels = new HashSet<>();
            for (JsonNode model : data) {
                String id = model.has("id") ? model.get("id").asText() : null;
                if (id == null || id.isEmpty()) continue;
                respModels.add(id);
            }

            // Load existing instances mapped by identity
            var allInstances = instanceRepo.findAllWithVendor();
            Map<String, Instance> existingMap = new HashMap<>();
            for (Instance inst : allInstances) {
                if (inst.getVendorId() != v.getId()) continue;
                String identity = inst.getUpstreamModel();
                if (identity == null || identity.isEmpty()) identity = inst.getModelName();
                existingMap.put(identity, inst);
            }

            int created = 0, deprecated = 0;

            for (String id : respModels) {
                if (existingMap.containsKey(id)) {
                    Instance inst = existingMap.get(id);
                    // Undeprecate if needed
                    if (inst.getStatus() == InstanceRepo.STATUS_DEPRECATED) {
                        // §J2-D1: restore to PrevStatus, not forced TAGGED
                        inst.setStatus(InstanceRepo.STATUS_RAW);
                        inst.setMeta(mergeVendorTags(inst.getMeta(), v.getMeta()));
                        instanceRepo.update(inst);
                    }
                    existingMap.remove(id);
                } else {
                    // Create new raw instance
                    Instance newInst = new Instance();
                    newInst.setModelName(id);
                    newInst.setUpstreamModel(id);
                    newInst.setVendorId(v.getId());
                    newInst.setStatus(InstanceRepo.STATUS_RAW);
                    newInst.setMeta(mergeVendorTags("{}", v.getMeta()));
                    instanceRepo.insert(newInst);
                    created++;
                }
            }

            // Remaining → deprecate
            for (Instance stale : existingMap.values()) {
                if (stale.getStatus() == InstanceRepo.STATUS_RAW
                    || stale.getStatus() == InstanceRepo.STATUS_TAGGED) {
                    stale.setStatus(InstanceRepo.STATUS_DEPRECATED);
                    instanceRepo.update(stale);
                    deprecated++;
                }
            }

            return new OneResult(created, deprecated, errors);
        } catch (Exception e) {
            errors.add("vendor " + v.getName() + ": " + e.getMessage());
            return new OneResult(0, 0, errors);
        }
    }

    @SuppressWarnings("unchecked")
    private String mergeVendorTags(String instMeta, String vendorMeta) {
        try {
            var iMeta = mapper.readTree(instMeta);
            var vMeta = mapper.readTree(vendorMeta);
            var vTags = vMeta.get("tags");

            if (vTags != null && vTags.isArray()) {
                var iTags = iMeta.get("tags");
                Set<String> tagSet = new HashSet<>();
                if (iTags != null && iTags.isArray()) {
                    iTags.forEach(t -> tagSet.add(t.asText()));
                }
                vTags.forEach(t -> tagSet.add(t.asText()));

                var merged = mapper.createObjectNode();
                merged.setAll((com.fasterxml.jackson.databind.node.ObjectNode) iMeta);
                var arr = merged.putArray("tags");
                tagSet.forEach(arr::add);
                return mapper.writeValueAsString(merged);
            }
        } catch (Exception ignore) {}
        return instMeta;
    }
}
