package com.oneapi.controller;

import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class VirtualModelController extends BaseController {
    private final VirtualModelRepo repo = new VirtualModelRepo();

    public void getAll(RoutingContext ctx) {
        var models = repo.findAll();
        var arr = new JsonArray();
        for (var virtualModel : models) {
            arr.add(new JsonObject()
                .put("id", virtualModel.getId())
                .put("name", virtualModel.getName())
                .put("match", virtualModel.getMatch()));
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
        VirtualModel virtualModel = repo.findById(id);
        if (virtualModel == null) {
            json(ctx, 404, "virtual model not found");
            return;
        }
        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject()
                .put("success", true)
                .put("message", "")
                .put("data", new JsonObject()
                    .put("id", virtualModel.getId())
                    .put("name", virtualModel.getName())
                    .put("match", virtualModel.getMatch()))
                .toString());
    }

    public void create(RoutingContext ctx) {
        ctx.body(); // force read body buffer
        if (ctx.getBody() == null) {
            json(ctx, 400, "request body is required");
            return;
        }
        var body = ctx.getBody().toJsonObject();
        if (body == null) {
            json(ctx, 400, "invalid JSON body");
            return;
        }
        String name = body.getString("name");
        String match = body.getString("match", "{}");

        if (name == null || name.isEmpty()) {
            json(ctx, 400, "model name is required");
            return;
        }

        VirtualModel virtualModel = new VirtualModel();
        virtualModel.setName(name);
        virtualModel.setMatch(match);
        repo.insert(virtualModel);

        ctx.response()
            .putHeader("Content-Type", "application/json")
            .end(new JsonObject().put("success", true).put("message", "").toString());
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        ctx.body(); // force read body buffer
        var body = ctx.getBody().toJsonObject();
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
        ok(ctx);
    }
}
