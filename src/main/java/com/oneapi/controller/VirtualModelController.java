package com.oneapi.controller;

import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class VirtualModelController {
    private final VirtualModelRepo repo = new VirtualModelRepo();

    public void getAll(RoutingContext ctx) {
        var models = repo.findAll();
        var arr = new JsonArray();
        for (var vm : models) {
            arr.add(new JsonObject()
                .put("id", vm.getId())
                .put("name", vm.getName())
                .put("match", vm.getMatch()));
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
        VirtualModel vm = repo.findById(id);
        if (vm == null) {
            json(ctx, 404, "virtual model not found");
            return;
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", new JsonObject()
                    .put("id", vm.getId())
                    .put("name", vm.getName())
                    .put("match", vm.getMatch()))
                .toString());
    }

    public void create(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        String name = body.getString("name");
        String match = body.getString("match", "{}");

        if (name == null || name.isEmpty()) {
            json(ctx, 400, "model name is required");
            return;
        }

        VirtualModel vm = new VirtualModel();
        vm.setName(name);
        vm.setMatch(match);
        repo.insert(vm);

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        var body = ctx.body().asJsonObject();
        String match = body.getString("match");
        if (match == null) {
            json(ctx, 400, "match is required");
            return;
        }
        repo.updateMatch(id, match);
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

    private void json(RoutingContext ctx, int status, String msg) {
        ctx.response().setStatusCode(status)
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", status < 400)
                .put("message", msg).toString());
    }
}
