package com.oneapi.controller;

import com.oneapi.repo.ModelCatalogRepo;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for ModelCatalogController.
 * <p>
 * Uses an in-memory H2 database (PostgreSQL compatibility mode) injected into ModelCatalogRepo.
 * Response verification uses string contains to avoid Vert.x JsonObject vs stdlib collection type mismatches.
 */
class ModelCatalogControllerTest {

    private DataSource ds;
    private ModelCatalogController controller;

    @BeforeEach
    void setup() throws Exception {
        ds = com.oneapi.config.TestDatabaseConfig.createDataSource();
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE model_catalog (
                    name VARCHAR(255) PRIMARY KEY,
                    capabilities TEXT,
                    context_window INTEGER,
                    input_price REAL,
                    output_price REAL,
                    reference_notes TEXT)
                """);
        }
        controller = new ModelCatalogController(new ModelCatalogRepo(ds));
    }

    // ── getAll ──

    @Test
    void getAll_returnsEmptyList() {
        String body = invoke(ctx -> controller.getAll(ctx));

        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("\"data\":[]");
    }

    @Test
    void getAll_returnsEntries() throws Exception {
        insertCatalog("deepseek-v4-flash", "[\"code\",\"chat\"]", 128000, 0.1, 0.3);

        String body = invoke(ctx -> controller.getAll(ctx));

        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("deepseek-v4-flash");
    }

    // ── getOne ──

    @Test
    void getOne_returnsEntryWhenFound() throws Exception {
        insertCatalog("deepseek-v4-flash", "[\"code\",\"chat\"]", 128000, 0.1, 0.3);

        String body = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("deepseek-v4-flash");
            controller.getOne(ctx);
        });

        assertThat(body).contains("\"success\":true");
        assertThat(body).contains("deepseek-v4-flash");
    }

    @Test
    void getOne_returns404WhenNotFound() {
        String body = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("nonexistent-model");
            controller.getOne(ctx);
        });

        assertThat(body).contains("\"success\":false");
        assertThat(body).contains("not found");
    }

    // ── create ──

    @Test
    void create_createsNewEntry() {
        JsonObject body = new JsonObject()
            .put("name", "deepseek-v4-pro")
            .put("capabilities", "[\"code\",\"chat\"]")
            .put("context_window", 256000)
            .put("input_price", 0.2)
            .put("output_price", 0.6);

        String resp = invoke(ctx -> {
            mockBody(ctx, body);
            controller.create(ctx);
        });

        assertThat(resp).contains("\"success\":true");
    }

    @Test
    void create_returns400WhenNameMissing() {
        JsonObject body = new JsonObject().put("capabilities", "[]");

        String resp = invoke(ctx -> {
            mockBody(ctx, body);
            controller.create(ctx);
        });

        assertThat(resp).contains("\"success\":false");
        assertThat(resp).contains("name is required");
    }

    @Test
    void create_returns400WhenNameDuplicate() throws Exception {
        insertCatalog("duplicate-model", "[\"chat\"]", 0, null, null);

        JsonObject body = new JsonObject().put("name", "duplicate-model");

        String resp = invoke(ctx -> {
            mockBody(ctx, body);
            controller.create(ctx);
        });

        assertThat(resp).contains("\"success\":false");
        assertThat(resp).contains("already exists");
    }

    // ── update ──

    @Test
    void update_modifiesExistingEntry() throws Exception {
        insertCatalog("update-test", "[\"chat\"]", 0, null, null);

        JsonObject body = new JsonObject()
            .put("name", "update-test")
            .put("capabilities", "[\"code\",\"vision\"]");

        String resp = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("update-test");
            mockBody(ctx, body);
            controller.update(ctx);
        });

        assertThat(resp).contains("\"success\":true");
    }

    @Test
    void update_returns404WhenNotFound() {
        String resp = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("nonexistent");
            controller.update(ctx);
        });

        assertThat(resp).contains("\"success\":false");
        assertThat(resp).contains("not found");
    }

    // ── delete ──

    @Test
    void delete_removesEntry() throws Exception {
        insertCatalog("to-delete", "[\"chat\"]", 0, null, null);

        String resp = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("to-delete");
            controller.delete(ctx);
        });

        assertThat(resp).contains("\"success\":true");
    }

    @Test
    void delete_returns404WhenNotFound() {
        String resp = invokeWithPath(ctx -> {
            when(ctx.pathParam("name")).thenReturn("nonexistent");
            controller.delete(ctx);
        });

        assertThat(resp).contains("\"success\":false");
        assertThat(resp).contains("not found");
    }

    // ── helpers ──

    /** Simple invoke for handlers that only need response mocked. */
    private String invoke(Handler runner) {
        RoutingContext ctx = mock(RoutingContext.class);
        String[] captured = {""};
        io.vertx.core.http.HttpServerResponse resp = mockResp(captured);
        when(ctx.response()).thenReturn(resp);
        runner.handle(ctx);
        return captured[0];
    }

    /** Invoke with path param stub already set (no further stubbing after runner runs). */
    private String invokeWithPath(Handler runner) {
        RoutingContext ctx = mock(RoutingContext.class);
        String[] captured = {""};
        io.vertx.core.http.HttpServerResponse resp = mockResp(captured);
        when(ctx.response()).thenReturn(resp);
        runner.handle(ctx);
        return captured[0];
    }

    private io.vertx.core.http.HttpServerResponse mockResp(String[] captured) {
        io.vertx.core.http.HttpServerResponse resp = mock(io.vertx.core.http.HttpServerResponse.class);
        when(resp.setStatusCode(anyInt())).thenReturn(resp);
        when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
        doAnswer(inv -> {
            captured[0] = inv.getArgument(0);
            return null;
        }).when(resp).end(anyString());
        return resp;
    }

    private void mockBody(RoutingContext ctx, JsonObject body) {
        RequestBody reqBody = mock(RequestBody.class);
        when(reqBody.asJsonObject()).thenReturn(body);
        when(ctx.body()).thenReturn(reqBody);
        // Also mock getBody() which is used by the controller
        when(ctx.getBody()).thenReturn(io.vertx.core.buffer.Buffer.buffer(body.encode()));
    }

    private void insertCatalog(String name, String caps, int ctxWindow, Double inputPrice, Double outputPrice) throws Exception {
        StringBuilder sql = new StringBuilder("INSERT INTO model_catalog (name, capabilities");
        StringBuilder vals = new StringBuilder("VALUES ('" + name + "','" + caps + "'");
        if (ctxWindow > 0) {
            sql.append(",context_window");
            vals.append(",").append(ctxWindow);
        }
        if (inputPrice != null) {
            sql.append(",input_price");
            vals.append(",").append(inputPrice);
        }
        if (outputPrice != null) {
            sql.append(",output_price");
            vals.append(",").append(outputPrice);
        }
        sql.append(") ").append(vals).append(")");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql.toString());
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(RoutingContext ctx);
    }
}
