package com.oneapi.controller;

import com.oneapi.model.Instance;
import com.oneapi.repo.InstanceRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class InstanceController {
    private final InstanceRepo repo = new InstanceRepo();

    public void getAll(RoutingContext ctx) {
        var instances = repo.findAll();
        var arr = new JsonArray();
        for (Instance i : instances) {
            arr.add(toJson(i));
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", arr)
                .toString());
    }

    public void getOne(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Instance i = repo.findById(id);
        if (i == null) {
            json(ctx, 404, "instance not found");
            return;
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", toJson(i))
                .toString());
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Instance existing = repo.findById(id);
        if (existing == null) {
            json(ctx, 404, "instance not found");
            return;
        }
        var body = ctx.body().asJsonObject();
        if (body.containsKey("status")) existing.setStatus(body.getInteger("status"));
        if (body.containsKey("meta")) existing.setMeta(body.getString("meta"));
        repo.update(existing);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void toggle(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        repo.toggleStatus(id);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void delete(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        repo.delete(id);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    private JsonObject toJson(Instance i) {
        return new JsonObject()
            .put("id", i.getId())
            .put("model_name", i.getModelName())
            .put("status", i.getStatus())
            .put("upstream_model", i.getUpstreamModel())
            .put("vendor_id", i.getVendorId())
            .put("created_time", i.getCreatedTime())
            .put("meta", i.getMeta());
    }

    private void json(RoutingContext ctx, int status, String msg) {
        ctx.response().setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", status < 400)
                .put("message", msg).toString());
    }
}
