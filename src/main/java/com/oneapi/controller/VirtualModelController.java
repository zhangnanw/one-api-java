package com.oneapi.controller;

import com.oneapi.model.VirtualModel;
import com.oneapi.repo.VirtualModelRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VirtualModelController extends BaseController {
    private final VirtualModelRepo repo;

    public VirtualModelController(VirtualModelRepo repo) {
        this.repo = repo;
    }

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
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
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
        var body = requireBody(ctx);
        if (body == null) return;
        String name = body.getString("name");
        String match = body.getString("match", "{}");

        if (name == null || name.isEmpty()) {
            badRequest(ctx, "model name is required");
            return;
        }
        if (repo.findByName(name) != null) {
            badRequest(ctx, "virtual model already exists: " + name);
            return;
        }

        VirtualModel virtualModel = new VirtualModel();
        virtualModel.setName(name);
        virtualModel.setMatch(match);
        try {
            repo.insert(virtualModel);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "virtual model create");
        }
    }

    public void update(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        VirtualModel existing = repo.findById(id);
        if (existing == null) {
            notFound(ctx, "virtual model");
            return;
        }
        var body = requireBody(ctx);
        if (body == null) return;
        String match = body.getString("match");
        if (match == null) {
            badRequest(ctx, "match is required");
            return;
        }
        try {
            repo.updateMatch(id, match);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "virtual model update");
        }
    }

    public void delete(RoutingContext ctx) {
        Integer id = parseIntParam(ctx, "id");
        if (id == null) return;
        try {
            repo.delete(id);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "virtual model delete");
        }
    }
}
