package com.oneapi.controller;

import com.oneapi.model.ModelCatalogEntry;
import com.oneapi.repo.ModelCatalogRepo;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * REST controller for model_catalog entries.
 * <p>
 * All responses follow the envelope:
 * {@code { "success": boolean, "message": string, "data": object (optional) }}
 */
public class ModelCatalogController extends BaseController {
    private final ModelCatalogRepo repo;

    public ModelCatalogController() {
        this.repo = new ModelCatalogRepo();
    }

    public ModelCatalogController(ModelCatalogRepo repo) {
        this.repo = repo;
    }

    /** GET /api/model-catalog — list all entries. */
    public void getAll(RoutingContext ctx) {
        var list = repo.findAll();
        var arr = new io.vertx.core.json.JsonArray();
        for (ModelCatalogEntry e : list) {
            arr.add(toJson(e));
        }
        ok(ctx, new JsonObject().put("data", arr));
    }

    /** GET /api/model-catalog/:name — get one entry. */
    public void getOne(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        ModelCatalogEntry entry = repo.findByName(name);
        if (entry == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        ok(ctx, toJson(entry));
    }

    /** POST /api/model-catalog — create a new entry. */
    public void create(RoutingContext ctx) {
        var body = ctx.body().asJsonObject();
        String name = body.getString("name");
        if (name == null || name.isEmpty()) {
            badRequest(ctx, "name is required");
            return;
        }
        if (repo.findByName(name) != null) {
            badRequest(ctx, "model_catalog entry already exists: " + name);
            return;
        }
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName(name);
        entry.setCapabilities(body.getString("capabilities"));
        entry.setContextWindow(body.getInteger("context_window"));
        entry.setInputPrice(body.getDouble("input_price"));
        entry.setOutputPrice(body.getDouble("output_price"));
        repo.insert(entry);
        ok(ctx);
    }

    /** PUT /api/model-catalog/:name — update an existing entry. */
    public void update(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (repo.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        var body = ctx.body().asJsonObject();
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName(body.getString("name", name));
        entry.setCapabilities(body.getString("capabilities"));
        entry.setContextWindow(body.getInteger("context_window"));
        entry.setInputPrice(body.getDouble("input_price"));
        entry.setOutputPrice(body.getDouble("output_price"));
        repo.update(name, entry);
        ok(ctx);
    }

    /** DELETE /api/model-catalog/:name — delete an entry. */
    public void delete(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (repo.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        repo.delete(name);
        ok(ctx);
    }

    private JsonObject toJson(ModelCatalogEntry e) {
        var obj = new JsonObject();
        obj.put("name", e.getName());
        obj.put("capabilities", e.getCapabilities());
        obj.put("context_window", e.getContextWindow());
        obj.put("input_price", e.getInputPrice());
        obj.put("output_price", e.getOutputPrice());
        return obj;
    }
}
