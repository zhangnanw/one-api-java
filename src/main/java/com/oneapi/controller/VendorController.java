package com.oneapi.controller;

import com.oneapi.model.Vendor;
import com.oneapi.repo.VendorRepo;
import com.oneapi.service.VendorRefreshService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class VendorController extends BaseController {
    private final VendorRepo repo = new VendorRepo();

    public void getAll(RoutingContext ctx) {
        int page = parseInt(ctx.request().getParam("page"), 0);
        int pageSize = parseInt(ctx.request().getParam("page_size"), 50);
        int offset = page * pageSize;

        var results = repo.findAllWithCounts(offset, pageSize);
        var arr = new JsonArray();
        for (var routedVendor : results) {
            JsonObject obj = toJson(routedVendor.vendor());
            obj.put("instance_count", routedVendor.instanceCount());
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
        Vendor vendor = repo.findById(id);
        if (vendor == null) {
            notFound(ctx, "vendor");
            return;
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", toJson(vendor))
                .toString());
    }

    public void create(RoutingContext ctx) {
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            badRequest(ctx, "request body is required");
            return;
        }
        Vendor vendor = parseBody(ctx);
        if (vendor == null) return; // parseBody already sent error
        if (vendor.getName() == null || vendor.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        if (vendor.getBaseUrl() == null || vendor.getBaseUrl().isEmpty()) {
            badRequest(ctx, "base_url is required");
            return;
        }
        // Default status
        if (vendor.getStatus() == 0) vendor.setStatus(1);
        repo.insert(vendor);
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void update(RoutingContext ctx) {
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            badRequest(ctx, "request body is required");
            return;
        }
        int id = Integer.parseInt(ctx.pathParam("id"));
        Vendor vendor = parseBody(ctx);
        if (vendor == null) return; // parseBody already sent error
        if (vendor.getName() == null || vendor.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        vendor.setId(id);
        repo.update(id, vendor);
        if (vendor.getApiKey() != null && !vendor.getApiKey().isEmpty()) {
            repo.updateApiKey(id, vendor.getApiKey());
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
        ctx.vertx().executeBlocking(svc::refreshAll).onSuccess(result -> {
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

    private JsonObject toJson(Vendor vendor) {
        return new JsonObject()
            .put("id", vendor.getId())
            .put("name", vendor.getName())
            .put("description", vendor.getDescription())
            .put("status", vendor.getStatus())
            .put("group", vendor.getGroup())
            .put("priority", vendor.getPriority())
            .put("created_time", vendor.getCreatedTime())
            .put("base_url", vendor.getBaseUrl())
            .put("api_key", vendor.getApiKey())
            .put("meta", vendor.getMeta());
    }

    private Vendor parseBody(RoutingContext ctx) {
        var body = ctx.getBody().toJsonObject();
        if (body == null) {
            badRequest(ctx, "invalid JSON body");
            return null;
        }
        Vendor vendor = new Vendor();
        if (body.containsKey("name")) vendor.setName(body.getString("name"));
        if (body.containsKey("description")) vendor.setDescription(body.getString("description"));
        if (body.containsKey("status")) vendor.setStatus(body.getInteger("status"));
        if (body.containsKey("group")) vendor.setGroup(body.getString("group"));
        if (body.containsKey("priority")) vendor.setPriority(body.getInteger("priority", 0));
        if (body.containsKey("base_url")) vendor.setBaseUrl(body.getString("base_url"));
        if (body.containsKey("api_key")) vendor.setApiKey(body.getString("api_key"));
        if (body.containsKey("meta")) vendor.setMeta(body.getString("meta"));
        return vendor;
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
