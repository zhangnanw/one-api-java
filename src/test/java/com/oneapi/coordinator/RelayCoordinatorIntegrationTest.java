package com.oneapi.coordinator;

import com.oneapi.config.AppConfig;
import com.oneapi.config.RouterConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.http.HttpServer;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class RelayCoordinatorIntegrationTest {

    private static HikariDataSource ds;
    private HttpServer server;
    private RouterConfig routerConfig;
    private int port;

    @BeforeAll
    static void initDb() throws Exception {
        ds = com.oneapi.config.TestDatabaseConfig.createDataSource();

        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            // vendors
            stmt.execute("CREATE TABLE vendors (" +
                "id SERIAL PRIMARY KEY, name TEXT NOT NULL, description TEXT, " +
                "status INTEGER DEFAULT 1, \"group\" TEXT, priority INTEGER DEFAULT 0, " +
                "created_time INTEGER, base_url TEXT, api_key TEXT, meta TEXT)");
            stmt.execute("INSERT INTO vendors (id, name, base_url, api_key) VALUES (1, 'deepseek', 'https://api.deepseek.com', 'sk-ds')");
            stmt.execute("INSERT INTO vendors (id, name, base_url, api_key) VALUES (2, 'minimax', 'https://api.minimax.chat', 'sk-mm')");

            // instances
            stmt.execute("CREATE TABLE instances (" +
                "id SERIAL PRIMARY KEY, model_name TEXT NOT NULL, status INTEGER DEFAULT 1, " +
                "upstream_model TEXT, vendor_id INTEGER REFERENCES vendors(id), created_time INTEGER, " +
                "meta TEXT, pref REAL DEFAULT 0, layer TEXT DEFAULT 'payg')");
            stmt.execute("INSERT INTO instances (id, model_name, status, upstream_model, vendor_id, meta, pref, layer) VALUES " +
                "(1, 'deepseek-v4-flash', 1, 'deepseek-chat', 1, '{}', 0, 'payg')");
            stmt.execute("INSERT INTO instances (id, model_name, status, upstream_model, vendor_id, meta, pref, layer) VALUES " +
                "(2, 'minimax-m2.7', 1, 'minimax-m2.7', 2, '{}', 0, 'payg')");
            stmt.execute("INSERT INTO instances (id, model_name, status, upstream_model, vendor_id, meta, pref, layer) VALUES " +
                "(3, 'minimax-m3', 1, 'minimax-m3', 2, '{}', 0, 'payg')");

            // virtual_models
            stmt.execute("CREATE TABLE virtual_models (id SERIAL PRIMARY KEY, name TEXT NOT NULL UNIQUE, match TEXT)");
            stmt.execute("INSERT INTO virtual_models (name, match) VALUES " +
                "('deepseek', '{\"models\":[\"deepseek-v4-flash\",\"deepseek-v4-pro\"]}')");
            stmt.execute("INSERT INTO virtual_models (name, match) VALUES " +
                "('minimax', '{\"models\":[\"minimax-m2.7\",\"minimax-m3\",\"minimax-m2.5\"]}')");

            // model_catalog
            stmt.execute("CREATE TABLE model_catalog (" +
                "name VARCHAR(255) PRIMARY KEY, capabilities TEXT, context_window INTEGER, " +
                "input_price REAL, output_price REAL, reference_notes TEXT)");
            stmt.execute("INSERT INTO model_catalog (name, capabilities, context_window) VALUES " +
                "('deepseek-v4-flash', '[\"chat\",\"code\"]', 131072)");
            stmt.execute("INSERT INTO model_catalog (name, capabilities, context_window) VALUES " +
                "('deepseek-v4-pro', '[\"chat\",\"code\"]', 131072)");
            stmt.execute("INSERT INTO model_catalog (name, capabilities, context_window) VALUES " +
                "('minimax-m2.7', '[\"chat\",\"code\"]', 131072)");
            stmt.execute("INSERT INTO model_catalog (name, capabilities, context_window) VALUES " +
                "('minimax-m3', '[\"chat\",\"code\",\"vision\"]', 131072)");
            stmt.execute("INSERT INTO model_catalog (name, capabilities, context_window) VALUES " +
                "('minimax-m2.5', '[\"chat\"]', 131072)");
        }
    }

    @AfterAll
    static void closeDb() {
        if (ds != null && !ds.isClosed()) ds.close();
    }

    @BeforeEach
    void startServer(Vertx vertx, VertxTestContext tc) {
        var config = new AppConfig();
        config.setServer(new AppConfig.ServerConfig());
        config.getServer().setPort(0); // random port
        config.setRelay(new AppConfig.RelayConfig());
        config.setPolicies(new AppConfig.PolicyConfig());
        config.getPolicies().getReasoning().setTriggerSuffix("-max");

        routerConfig = new RouterConfig(vertx, config, ds);
        Router router = routerConfig.build();

        vertx.createHttpServer().requestHandler(router).listen(0)
            .onComplete(tc.succeeding(s -> {
                server = s;
                port = s.actualPort();
                tc.completeNow();
            }));
    }

    @AfterEach
    void stopServer(VertxTestContext tc) {
        if (routerConfig != null) routerConfig.close();
        if (server != null) {
            server.close().onComplete(tc.succeedingThenComplete());
        } else {
            tc.completeNow();
        }
    }

    private io.vertx.core.Future<io.vertx.core.http.HttpClientResponse> post(Vertx vertx, JsonObject body) {
        return vertx.createHttpClient().request(HttpMethod.POST, port, "localhost", "/v1/chat/completions")
            .compose(req -> req
                .putHeader("Authorization", "Bearer sk-test")
                .putHeader("Content-Type", "application/json")
                .send(body.encode()));
    }

    @Test
    void validTextRequest_returns200(Vertx vertx, VertxTestContext tc) {
        JsonObject body = new JsonObject()
            .put("model", "deepseek")
            .put("messages", new JsonArray().add(new JsonObject().put("role", "user").put("content", "hi")))
            .put("max_tokens", 2);

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> {
                // 401=upstream auth fail (test key), 503=all retries exhausted
                // Both mean filter chain passed the request through
                int code = resp.statusCode();
                assertTrue(code == 200 || code == 401 || code == 503,
                    "expected 200/401/503 but was " + code);
            });
            tc.completeNow();
        }));
    }

    @Test
    void noModel_returns400(Vertx vertx, VertxTestContext tc) {
        JsonObject body = new JsonObject()
            .put("messages", new JsonArray().add(new JsonObject().put("role", "user").put("content", "hi")));

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> assertEquals(400, resp.statusCode()));
            tc.completeNow();
        }));
    }

    @Test
    void unregisteredModel_returns404(Vertx vertx, VertxTestContext tc) {
        JsonObject body = new JsonObject()
            .put("model", "nonexistent-xyz")
            .put("messages", new JsonArray().add(new JsonObject().put("role", "user").put("content", "hi")));

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> assertEquals(404, resp.statusCode()));
            tc.completeNow();
        }));
    }

    @Test
    void imageToNonVisionModel_returns400(Vertx vertx, VertxTestContext tc) {
        JsonObject body = new JsonObject()
            .put("model", "deepseek")
            .put("messages", new JsonArray().add(new JsonObject()
                .put("role", "user")
                .put("content", new JsonArray()
                    .add(new JsonObject()
                        .put("type", "image_url")
                        .put("image_url", new JsonObject().put("url", "data:image/png;base64,iVBOR")))
                    .add(new JsonObject().put("type", "text").put("text", "what")))))
            .put("max_tokens", 3);

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> assertEquals(400, resp.statusCode()));
            tc.completeNow();
        }));
    }

    @Test
    void imageToVisionModel_returns200(Vertx vertx, VertxTestContext tc) {
        JsonObject body = new JsonObject()
            .put("model", "minimax")
            .put("messages", new JsonArray().add(new JsonObject()
                .put("role", "user")
                .put("content", new JsonArray()
                    .add(new JsonObject()
                        .put("type", "image_url")
                        .put("image_url", new JsonObject().put("url", "data:image/png;base64,iVBOR")))
                    .add(new JsonObject().put("type", "text").put("text", "what")))))
            .put("max_tokens", 3);

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> {
                // 401=upstream auth fail (test key), 503=all retries exhausted
                int code = resp.statusCode();
                assertTrue(code == 200 || code == 401 || code == 503,
                    "expected 200/401/503 but was " + code);
            });
            tc.completeNow();
        }));
    }

    @Test
    void malformedJSON_returns400(Vertx vertx, VertxTestContext tc) {
        vertx.createHttpClient().request(HttpMethod.POST, port, "localhost", "/v1/chat/completions")
            .compose(req -> req
                .putHeader("Authorization", "Bearer sk-test")
                .putHeader("Content-Type", "application/json")
                .send("{bad"))
            .onComplete(tc.succeeding(resp -> {
                tc.verify(() -> assertEquals(400, resp.statusCode()));
                tc.completeNow();
            }));
    }

    @Test
    void oversizedBody_returns413(Vertx vertx, VertxTestContext tc) {
        // > 524288 bytes (131072 tokens × 4)
        String padding = "x".repeat(600_000);
        JsonObject body = new JsonObject()
            .put("model", "deepseek")
            .put("messages", new JsonArray().add(new JsonObject().put("role", "user").put("content", padding)))
            .put("max_tokens", 1);

        post(vertx, body).onComplete(tc.succeeding(resp -> {
            tc.verify(() -> assertEquals(413, resp.statusCode()));
            tc.completeNow();
        }));
    }
}
