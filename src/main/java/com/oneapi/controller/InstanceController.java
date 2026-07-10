package com.oneapi.controller;

import com.oneapi.model.Instance;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.VendorRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstanceController extends BaseController {
    private final InstanceRepo repo;
    private final VendorRepo vendorRepo;

    public InstanceController(InstanceRepo instanceRepo, VendorRepo vendorRepo) {
        this.repo = instanceRepo;
        this.vendorRepo = vendorRepo;
    }

    public void getAll(RoutingContext ctx) {
        var instances = repo.findAll();
        var arr = new JsonArray();
        for (Instance instance : instances) {
            arr.add(toJson(instance));
        }
        ok(ctx, arr);
    }

    public void getOne(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        Instance instance = repo.findById(id);
        if (instance == null) {
            notFound(ctx, "instance");
            return;
        }
        ok(ctx, toJson(instance));
    }

    public void create(RoutingContext ctx) {
        var body = requireBody(ctx);
        if (body == null) return;
        String modelName = body.getString("model_name");
        if (modelName == null || modelName.isEmpty()) {
            badRequest(ctx, "model_name is required");
            return;
        }
        Integer vendorId = body.getInteger("vendor_id");
        if (vendorId == null) {
            badRequest(ctx, "vendor_id is required");
            return;
        }
        if (vendorRepo.findById(vendorId) == null) {
            notFound(ctx, "vendor");
            return;
        }
        Instance inst = new Instance();
        inst.setModelName(modelName);
        inst.setVendorId(vendorId);
        inst.setUpstreamModel(body.getString("upstream_model", modelName));
        inst.setStatus(body.getInteger("status", 1));
        inst.setMeta(body.getString("meta", "{}"));
        inst.setPref(body.getFloat("pref", 0.5f));
        inst.setLayer(body.getString("layer", "payg"));
        try {
            repo.insert(inst);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "instance create");
        }
    }

    public void update(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        Instance existing = repo.findById(id);
        if (existing == null) {
            notFound(ctx, "instance");
            return;
        }
        var body = requireBody(ctx);
        if (body == null) return;
        if (body.containsKey("model_name")) existing.setModelName(body.getString("model_name"));
        if (body.containsKey("upstream_model")) existing.setUpstreamModel(body.getString("upstream_model"));
        if (body.containsKey("vendor_id")) {
            Integer vendorId = body.getInteger("vendor_id");
            if (vendorId == null) {
                badRequest(ctx, "vendor_id must be an integer");
                return;
            }
            if (vendorRepo.findById(vendorId) == null) {
                notFound(ctx, "vendor");
                return;
            }
            existing.setVendorId(vendorId);
        }
        if (body.containsKey("status")) {
            Integer status = body.getInteger("status");
            if (status == null) {
                badRequest(ctx, "status must be an integer");
                return;
            }
            existing.setStatus(status);
        }
        if (body.containsKey("meta")) existing.setMeta(body.getString("meta"));
        if (body.containsKey("pref")) {
            Float pref = body.getFloat("pref");
            if (pref == null) {
                badRequest(ctx, "pref must be a float");
                return;
            }
            existing.setPref(pref);
        }
        if (body.containsKey("layer")) existing.setLayer(body.getString("layer"));
        try {
            repo.update(existing);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "instance update");
        }
    }

    public void toggle(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        try {
            repo.toggleStatus(id);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "instance toggle");
        }
    }

    public void delete(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        try {
            repo.delete(id);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "instance delete");
        }
    }

    private JsonObject toJson(Instance instance) {
        return new JsonObject()
            .put("id", instance.getId())
            .put("model_name", instance.getModelName())
            .put("status", instance.getStatus())
            .put("upstream_model", instance.getUpstreamModel())
            .put("vendor_id", instance.getVendorId())
            .put("created_time", instance.getCreatedTime())
            .put("meta", instance.getMeta())
            .put("pref", instance.getPref())
            .put("layer", instance.getLayer());
    }
}
