package com.oneapi.controller;

import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VirtualModelController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(VirtualModelController.class);
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
        ok(ctx, arr);
    }

    public void getOne(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        VirtualModel virtualModel = repo.findById(id);
        if (virtualModel == null) {
            notFound(ctx, "virtual model");
            return;
        }
        ok(ctx, new JsonObject()
            .put("id", virtualModel.getId())
            .put("name", virtualModel.getName())
            .put("match", virtualModel.getMatch()));
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
        String name = body.getString("name");
        String match = body.getString("match", "{}");

        if (name == null || name.isEmpty()) {
            badRequest(ctx, "model name is required");
            return;
        }

        VirtualModel virtualModel = new VirtualModel();
        virtualModel.setName(name);
        virtualModel.setMatch(match);
        try {
            repo.insert(virtualModel);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("virtual model create failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    public void update(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        ctx.body(); // force read body buffer
        var body = ctx.getBody().toJsonObject();
        String match = body.getString("match");
        if (match == null) {
            badRequest(ctx, "match is required");
            return;
        }
        repo.updateMatch(id, match);
        ok(ctx);
    }

    public void delete(RoutingContext ctx) {
        int id = Integer.parseInt(ctx.pathParam("id"));
        try {
            repo.delete(id);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("virtual model delete failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }
}
