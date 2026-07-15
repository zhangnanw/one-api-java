package com.oneapi.controller;

import com.oneapi.background.balance.BalanceInfo;
import com.oneapi.background.BalanceQueryService;
import com.oneapi.background.VendorRefreshService;
import com.oneapi.model.Vendor;
import com.oneapi.service.VendorService;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VendorController extends BaseController {
    private final VendorService vendorService;
    private final VendorRefreshService refreshService;
    private final BalanceQueryService balanceService;

    public VendorController(VendorService vendorService, VendorRefreshService refreshService,
                            BalanceQueryService balanceService) {
        this.vendorService = vendorService;
        this.refreshService = refreshService;
        this.balanceService = balanceService;
    }

    public void getAll(RoutingContext ctx) {
        int page = parseInt(ctx.request().getParam("page"), 0);
        int pageSize = parseInt(ctx.request().getParam("page_size"), 50);
        int offset = page * pageSize;

        var results = vendorService.findAllWithCounts(offset, pageSize);
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
        Vendor vendor = vendorService.findById(id);
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
        if (vendor == null) return;
        if (vendor.getName() == null || vendor.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        if (vendor.getBaseUrl() == null || vendor.getBaseUrl().isEmpty()) {
            badRequest(ctx, "base_url is required");
            return;
        }
        if (vendor.getStatus() == 0) vendor.setStatus(1);
        try {
            vendorService.insert(vendor);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "vendor create");
        }
    }

    public void update(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        if (vendorService.findById(id) == null) {
            notFound(ctx, "vendor");
            return;
        }
        var body = requireBody(ctx);
        if (body == null) return;
        Vendor vendor = parseBody(ctx, body);
        if (vendor == null) return;
        if (vendor.getName() == null || vendor.getName().isEmpty()) {
            badRequest(ctx, "vendor name is required");
            return;
        }
        try {
            vendorService.update(id, vendor);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "vendor update");
        }
    }

    public void delete(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        try {
            vendorService.delete(id);
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
        BalanceInfo info = balanceService.getBalance(id);
        if (info == null) {
            ctx.response().setStatusCode(404)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", "no balance data for vendor " + id)
                    .toString());
            return;
        }
        ok(ctx, new JsonObject()
            .put("vendor_id", info.vendorId())
            .put("vendor_name", info.vendorName())
            .put("available", info.available())
            .put("total_balance", info.totalBalance())
            .put("currency", info.currency()));
    }

    public void queryAllBalances(RoutingContext ctx) {
        ctx.vertx().executeBlocking(balanceService::queryAll).onSuccess(result -> {
            JsonArray results = new JsonArray();
            for (BalanceInfo info : result.values()) {
                results.add(new JsonObject()
                    .put("vendor_id", info.vendorId())
                    .put("vendor_name", info.vendorName())
                    .put("available", info.available())
                    .put("total_balance", info.totalBalance())
                    .put("currency", info.currency()));
            }
            ok(ctx, new JsonObject()
                .put("success", true)
                .put("queried", result.size())
                .put("results", results));
        }).onFailure(err -> {
            ctx.response().setStatusCode(500)
                .putHeader("Content-Type", "application/json")
                .end(new JsonObject()
                    .put("success", false)
                    .put("message", err.getMessage())
                    .toString());
        });
    }

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
