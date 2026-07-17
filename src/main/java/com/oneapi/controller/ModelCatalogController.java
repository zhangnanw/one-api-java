package com.oneapi.controller;

import com.oneapi.entity.ModelCatalogEntry;
import com.oneapi.service.ModelCatalogService;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for model_catalog entries.
 * <p>
 * All responses follow the envelope:
 * {@code { "success": boolean, "message": string, "data": object (optional) }}
 */
@Slf4j
public class ModelCatalogController extends BaseController {
    private final ModelCatalogService modelCatalogService;

    public ModelCatalogController(ModelCatalogService modelCatalogService) {
        this.modelCatalogService = modelCatalogService;
    }

    /** GET /api/model-catalog — list all entries. */
    public void getAll(RoutingContext ctx) {
        var list = modelCatalogService.findAll();
        var arr = new io.vertx.core.json.JsonArray();
        for (ModelCatalogEntry e : list) {
            arr.add(toJson(e));
        }
        ok(ctx, arr);
    }

    /** GET /api/model-catalog/:name — get one entry. */
    public void getOne(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        ModelCatalogEntry entry = modelCatalogService.findByName(name);
        if (entry == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        ok(ctx, toJson(entry));
    }

    /** POST /api/model-catalog — create a new entry. */
    public void create(RoutingContext ctx) {
        var body = requireBody(ctx);
        if (body == null) return;
        String name = body.getString("name");
        if (name == null || name.isEmpty()) {
            badRequest(ctx, "name is required");
            return;
        }
        if (modelCatalogService.findByName(name) != null) {
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
            modelCatalogService.insert(entry);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "model_catalog create");
        }
    }

    /** PUT /api/model-catalog/:name — update an existing entry. */
    public void update(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (modelCatalogService.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        var body = requireBody(ctx);
        if (body == null) return;
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName(body.getString("name", name));
        entry.setCapabilities(body.getString("capabilities"));
        entry.setContextWindow(body.getInteger("context_window"));
        entry.setInputPrice(body.getDouble("input_price"));
        entry.setOutputPrice(body.getDouble("output_price"));
        entry.setReferenceNotes(body.getString("reference_notes"));
        try {
            modelCatalogService.update(name, entry);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "model_catalog update");
        }
    }

    /** DELETE /api/model-catalog/:name — delete an entry. */
    public void delete(RoutingContext ctx) {
        String name = ctx.pathParam("name");
        if (modelCatalogService.findByName(name) == null) {
            notFound(ctx, "model_catalog");
            return;
        }
        try {
            modelCatalogService.delete(name);
            ok(ctx);
        } catch (RuntimeException e) {
            dbError(ctx, e, "model_catalog delete");
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
