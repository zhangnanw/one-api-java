package com.oneapi.controller;

import com.oneapi.model.Vendor;
import com.oneapi.repo.VendorRepo;
import com.oneapi.service.VendorRefreshService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VendorController {
    private static final Logger log = LoggerFactory.getLogger(VendorController.class);
    private final VendorRepo repo = new VendorRepo();

    public void getAll(RoutingContext ctx) {
        int page = parseInt(ctx.request().getParam("page"), 0);
        int pageSize = parseInt(ctx.request().getParam("page_size"), 50);
        int offset = page * pageSize;

        var vendors = repo.findAll(offset, pageSize);
        var arr = new JsonArray();
        for (Vendor v : vendors) {
            JsonObject obj = toJson(v);
            obj.put("instance_count", repo.countInstances(v.getId()));
            arr.add(obj);
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
        Vendor v = repo.findById(id);
        if (v == null) {
            notFound(ctx, "vendor");
            return;
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", toJson(v))
                .toString());
    }

    public void create(RoutingContext ctx) {
        Vendor v = parseBody(ctx);
        if (v.getName() == null || v.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        if (v.getBaseUrl() == null || v.getBaseUrl().isEmpty()) {
            badRequest(ctx, "base_url is required");
            return;
        }
        // Default status
        if (v.getStatus() == 0) v.setStatus(1);
        repo.insert(v);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        Vendor v = parseBody(ctx);
        if (v.getName() == null || v.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        v.setId(id);
        repo.update(id, v);
        if (v.getApiKey() != null && !v.getApiKey().isEmpty()) {
            repo.updateApiKey(id, v.getApiKey());
        }
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

    public void refreshModels(RoutingContext ctx) {
        var svc = new VendorRefreshService();
        ctx.vertx().executeBlocking(() -> {
            var result = svc.refreshAll();
            return result;
        }).onSuccess(result -> {
            ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", true)
                    .put("message", "refresh complete")
                    .put("created", result.created())
                    .put("deprecated", result.deprecated())
                    .put("errors", new JsonArray(result.errors()))
                    .toString());
        }).onFailure(err -> {
            ctx.response().setStatusCode(500)
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", err.getMessage())
                    .toString());
        });
    }

    // --- helpers ---

    private JsonObject toJson(Vendor v) {
        return new JsonObject()
            .put("id", v.getId())
            .put("name", v.getName())
            .put("description", v.getDescription())
            .put("status", v.getStatus())
            .put("group", v.getGroupName())
            .put("priority", v.getPriority())
            .put("created_time", v.getCreatedTime())
            .put("base_url", v.getBaseUrl())
            .put("api_key", v.getApiKey())
            .put("meta", v.getMeta());
    }

    private Vendor parseBody(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        Vendor v = new Vendor();
        if (body.containsKey("name")) v.setName(body.getString("name"));
        if (body.containsKey("description")) v.setDescription(body.getString("description"));
        if (body.containsKey("status")) v.setStatus(body.getInteger("status"));
        if (body.containsKey("group")) v.setGroupName(body.getString("group"));
        if (body.containsKey("priority")) v.setPriority(body.getInteger("priority", 0));
        if (body.containsKey("base_url")) v.setBaseUrl(body.getString("base_url"));
        if (body.containsKey("api_key")) v.setApiKey(body.getString("api_key"));
        if (body.containsKey("meta")) v.setMeta(body.getString("meta"));
        return v;
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultVal; }
    }

    private void notFound(RoutingContext ctx, String entity) {
        ctx.response().setStatusCode(404)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", false)
                .put("message", entity + " not found").toString());
    }

    private void badRequest(RoutingContext ctx, String msg) {
        ctx.response().setStatusCode(400)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", false)
                .put("message", msg).toString());
    }
}
