package com.oneapi.controller;

import com.oneapi.model.ModelCatalogEntry;
import com.oneapi.repo.ModelCatalogRepo;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST controller for model_catalog entries.
 * <p>
 * All responses follow the envelope:
 * {@code { "success": boolean, "message": string, "data": object (optional) }}
 */
public class ModelCatalogController extends BaseController {
    private static final Logger log = LoggerFactory.getLogger(ModelCatalogController.class);
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
        ok(ctx, arr);
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
        entry.setReferenceNotes(body.getString("reference_notes"));
        try {
            repo.insert(entry);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("model_catalog create failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    /** PUT /api/model-catalog/:name — update an existing entry. */
    public void update(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (repo.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
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
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName(body.getString("name", name));
        entry.setCapabilities(body.getString("capabilities"));
        entry.setContextWindow(body.getInteger("context_window"));
        entry.setInputPrice(body.getDouble("input_price"));
        entry.setOutputPrice(body.getDouble("output_price"));
        entry.setReferenceNotes(body.getString("reference_notes"));
        try {
            repo.update(name, entry);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("model_catalog update failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    /** DELETE /api/model-catalog/:name — delete an entry. */
    public void delete(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (repo.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        try {
            repo.delete(name);
            ok(ctx);
        } catch (RuntimeException e) {
            log.error("model_catalog delete failed: {}", e.getMessage());
            ctx.response().setStatusCode(500).end(new JsonObject().put("success", false).put("message", "Database error").toString());
        }
    }

    private JsonObject toJson(ModelCatalogEntry e) {
        var obj = new JsonObject();
        obj.put("name", e.getName());
        obj.put("capabilities", e.getCapabilities());
        obj.put("context_window", e.getContextWindow());
        obj.put("input_price", e.getInputPrice());
        obj.put("output_price", e.getOutputPrice());
        obj.put("reference_notes", e.getReferenceNotes());
        return obj;
    }
}
