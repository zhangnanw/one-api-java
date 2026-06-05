package com.oneapi;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oneapi.config.AppConfig;
import com.oneapi.config.DatabaseConfig;
import com.oneapi.config.RouterConfig;
import com.oneapi.service.RelayLogger;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();

        // Initialize database
        DatabaseConfig.init(config.sqlitePath());

        // Initialize relay-log.db (independent, silent failure)
        RelayLogger.init();

        // Create Vert.x
        Vertx vertx = Vertx.vertx();

        // Build router
        var routerConfig = new RouterConfig(vertx);
        var router = routerConfig.build();

        // Start HTTP server
        HttpServer server = vertx.createHttpServer()
            .requestHandler(router)
            .listen(config.port(), ar -> {
                if (ar.succeeded()) {
                    log.info("one-api-java started on http://localhost:{}", config.port());
                } else {
                    log.error("Failed to start: {}", ar.cause().getMessage());
                    System.exit(1);
                }
            });

        // Graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.close();
            vertx.close();
        }));
    }
}
