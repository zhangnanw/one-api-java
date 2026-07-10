package com.oneapi.controller;

import com.oneapi.model.Instance;
import com.oneapi.repo.InstanceRepo;
import com.oneapi.repo.VendorRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(InstanceController.class);
    private final InstanceRepo repo = new InstanceRepo();
    private final VendorRepo vendorRepo = new VendorRepo();

    public void getAll(RoutingContext ctx) {
        var instances = repo.findAll();
        var arr = new JsonArray();
        for (Instance instance : instances) {
            arr.add(toJson(instance));
        }
        ok(ctx, new JsonObject().put("data", arr));
    }

    public void getOne(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Instance instance = repo.findById(id);
        if (instance == null) {
            notFound(ctx, "instance");
            return;
        }
        ok(ctx, toJson(instance));
    }

    public void create(RoutingContext ctx) {
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            badRequest(ctx, "request body is required");
            return;
        }
        var body = ctx.getBody().toJsonObject();
        if (body == null) {
            badRequest(ctx, "invalid JSON body");
            return;
        }
        String modelName = body.getString("model_name");
        if (modelName == null || modelName.isEmpty()) {
            badRequest(ctx, "model_name is required");
            return;
        }
        if (!body.containsKey("vendor_id") || body.getInteger("vendor_id") == null) {
            badRequest(ctx, "vendor_id is required");
            return;
        }
        int vendorId = body.getInteger("vendor_id");
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
            log.error("instance create failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Instance existing = repo.findById(id);
        if (existing == null) {
            notFound(ctx, "instance");
            return;
        }
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            badRequest(ctx, "request body is required");
            return;
        }
        var body = ctx.getBody().toJsonObject();
        if (body == null) {
            badRequest(ctx, "invalid JSON body");
            return;
        }
        if (body.containsKey("model_name")) existing.setModelName(body.getString("model_name"));
        if (body.containsKey("upstream_model")) existing.setUpstreamModel(body.getString("upstream_model"));
        if (body.containsKey("vendor_id")) {
            int vendorId = body.getInteger("vendor_id");
            if (vendorRepo.findById(vendorId) == null) {
                notFound(ctx, "vendor");
                return;
            }
            existing.setVendorId(vendorId);
        }
        if (body.containsKey("status")) existing.setStatus(body.getInteger("status"));
        if (body.containsKey("meta")) existing.setMeta(body.getString("meta"));
        if (body.containsKey("pref")) existing.setPref(body.getFloat("pref"));
        if (body.containsKey("layer")) existing.setLayer(body.getString("layer"));
        try {
            repo.update(existing);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("instance update failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    public void toggle(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        repo.toggleStatus(id);
        ok(ctx);
    }

    public void delete(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        try {
            repo.delete(id);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("instance delete failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
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
