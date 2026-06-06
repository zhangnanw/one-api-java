package com.oneapi.controller;

import com.oneapi.model.Instance;
import com.oneapi.repo.InstanceRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class InstanceController extends BaseController {
    private final InstanceRepo repo = new InstanceRepo();

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

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Instance existing = repo.findById(id);
        if (existing == null) {
            notFound(ctx, "instance");
            return;
        }
        var body = ctx.body().asJsonObject();
        if (body.containsKey("status")) existing.setStatus(body.getInteger("status"));
        if (body.containsKey("meta")) existing.setMeta(body.getString("meta"));
        repo.update(existing);
        ok(ctx);
    }

    public void toggle(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        repo.toggleStatus(id);
        ok(ctx);
    }

    public void delete(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        repo.delete(id);
        ok(ctx);
    }

    private JsonObject toJson(Instance instance) {
        return new JsonObject()
            .put("id", instance.getId())
            .put("model_name", instance.getModelName())
            .put("status", instance.getStatus())
            .put("upstream_model", instance.getUpstreamModel())
            .put("vendor_id", instance.getVendorId())
            .put("created_time", instance.getCreatedTime())
            .put("meta", instance.getMeta());
    }
}
