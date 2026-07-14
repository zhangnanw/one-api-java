package com.oneapi.controller;

import com.oneapi.model.Vendor;
import com.oneapi.repo.VendorRepo;
import com.oneapi.background.VendorRefreshService;
import com.oneapi.background.BalanceQueryService;
import com.oneapi.background.balance.BalanceInfo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VendorController extends BaseController {
    private final VendorRepo repo;
    private final VendorRefreshService refreshService;
    private final BalanceQueryService balanceService;

    public VendorController(VendorRepo repo, VendorRefreshService refreshService) {
        this(repo, refreshService, null);
    }

    public VendorController(VendorRepo repo, VendorRefreshService refreshService, BalanceQueryService balanceService) {
        this.repo = repo;
        this.refreshService = refreshService;
        this.balanceService = balanceService;
    }

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

        ok(ctx, arr);
    }

    public void getOne(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        Vendor vendor = repo.findById(id);
        if (vendor == null) {
            notFound(ctx, "vendor");
            return;
        }
        ok(ctx, toJson(vendor));
    }

    public void create(RoutingContext ctx) {
        var body = requireBody(ctx);
        if (body == null) return;
        Vendor vendor = parseBody(ctx, body);
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
        try {
            repo.insert(vendor);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "vendor create");
        }
    }

    public void update(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        if (repo.findById(id) == null) {
            notFound(ctx, "vendor");
            return;
        }
        var body = requireBody(ctx);
        if (body == null) return;
        Vendor vendor = parseBody(ctx, body);
        if (vendor == null) return; // parseBody already sent error
        if (vendor.getName() == null || vendor.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        vendor.setId(id);
        try {
            repo.update(id, vendor);
            if (vendor.getApiKey() != null && !vendor.getApiKey().isEmpty()) {
                repo.updateApiKey(id, vendor.getApiKey());
            }
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "vendor update");
        }
    }

    public void delete(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        try {
            repo.delete(id);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "vendor delete");
        }
    }

    public void refreshModels(RoutingContext ctx) {
        ctx.vertx().executeBlocking(refreshService::refreshAll).onSuccess(result -> {
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
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", err.getMessage())
                    .toString());
        });
    }

    public void getBalance(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        if (balanceService == null) {
            ctx.response().setStatusCode(503)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "balance service not available")
                    .toString());
            return;
        }
        BalanceInfo info = balanceService.getBalance(id);
        if (info != null) {
            ok(ctx, new JsonObject()
                .put("vendor_id", info.vendorId())
                .put("vendor_name", info.vendorName())
                .put("available", info.available())
                .put("total_balance", info.totalBalance())
                .put("currency", info.currency()));
            return;
        }
        // 缓存未命中，按需查询
        Vendor vendor = repo.findById(id);
        if (vendor == null || vendor.getStatus() != 1) {
            ctx.response().setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "vendor " + id + " not found or disabled")
                    .toString());
            return;
        }
        BalanceInfo queried = balanceService.queryOne(vendor);
        if (queried == null) {
            ctx.response().setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "no balance provider for vendor " + vendor.getName())
                    .toString());
            return;
        }
        ok(ctx, new JsonObject()
            .put("vendor_id", queried.vendorId())
            .put("vendor_name", queried.vendorName())
            .put("available", queried.available())
            .put("total_balance", queried.totalBalance())
            .put("currency", queried.currency()));
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
            .put("balance_credential", vendor.getBalanceCredential())
            .put("meta", vendor.getMeta());
    }

    private Vendor parseBody(RoutingContext ctx, JsonObject body) {
        Vendor vendor = new Vendor();
        if (body.containsKey("name")) vendor.setName(body.getString("name"));
        if (body.containsKey("description")) vendor.setDescription(body.getString("description"));
        if (body.containsKey("status")) {
            Integer status = body.getInteger("status");
            if (status == null) {
                badRequest(ctx, "status must be an integer");
                return null;
            }
            vendor.setStatus(status);
        }
        if (body.containsKey("group")) vendor.setGroup(body.getString("group"));
        if (body.containsKey("priority")) {
            Integer priority = body.getInteger("priority");
            if (priority == null) {
                badRequest(ctx, "priority must be an integer");
                return null;
            }
            vendor.setPriority(priority);
        }
        if (body.containsKey("base_url")) vendor.setBaseUrl(body.getString("base_url"));
        if (body.containsKey("api_key")) vendor.setApiKey(body.getString("api_key"));
        if (body.containsKey("balance_credential")) vendor.setBalanceCredential(body.getString("balance_credential"));
        if (body.containsKey("meta")) vendor.setMeta(body.getString("meta"));
        return vendor;
    }

    private int parseInt(String val, int defaultVal) {
        if (val == null) return defaultVal;
        try { return Integer.parseInt(val); }
        catch (NumberFormatException e) { return defaultVal; }
    }
}
