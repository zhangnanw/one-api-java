package com.oneapi.core;

import com.oneapi.config.AppConfig;
import com.oneapi.config.RouterConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

/**
 * Spring Boot 启动完成后启动 Vert.x HTTP server。
 * <p>
 * 负责：
 * 1. 构建 Vert.x Router 并启动 HTTP server。
 * 2. 应用关闭时优雅停止 Vert.x。
 * <p>
 * 数据库、日志等组件已由 Spring 容器管理，此处不再手动桥接。
 */
@Slf4j
@Component
public class VertxApplicationRunner implements ApplicationRunner, DisposableBean {

    private final Vertx vertx;
    private final HttpServer server;
    private final RouterConfig routerConfig;
    private final AppConfig appConfig;

    public VertxApplicationRunner(Vertx vertx, RouterConfig routerConfig, AppConfig appConfig) {
        this.vertx = vertx;
        this.routerConfig = routerConfig;
        this.appConfig = appConfig;
        this.server = vertx.createHttpServer()
            .requestHandler(routerConfig.build());
    }

    @Override
    public void run(ApplicationArguments args) {
        server.listen(appConfig.port(), ar -> {
            if (ar.succeeded()) {
                log.info("one-api-java started on http://localhost:{}", appConfig.port());
            } else {
                log.error("Failed to start Vert.x HTTP server: {}", ar.cause().getMessage());
                System.exit(1);
            }
        });
    }

    @Override
    public void destroy() {
        log.info("Shutting down Vert.x...");
        server.close().toCompletionStage().toCompletableFuture().join();
        routerConfig.close();
        vertx.close().toCompletionStage().toCompletableFuture().join();
        log.info("Vert.x shutdown complete.");
    }
}
