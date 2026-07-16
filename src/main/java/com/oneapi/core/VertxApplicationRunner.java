package com.oneapi.core;

import com.oneapi.background.HolographicLogger;
import com.oneapi.config.AppConfig;
import com.oneapi.config.DatabaseConfig;
import com.oneapi.config.RouterConfig;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Spring Boot 启动完成后启动 Vert.x HTTP server。
 * <p>
 * 负责：
 * 1. 将 Spring 管理的 {@link DataSource} 桥接给旧版 {@link DatabaseConfig}。
 * 2. 初始化全息日志表（HolographicLogger）。
 * 3. 构建 Vert.x Router 并启动 HTTP server。
 * 4. 应用关闭时优雅停止 Vert.x。
 */
@Slf4j
@Component
public class VertxApplicationRunner implements ApplicationRunner, DisposableBean {

    private final Vertx vertx;
    private final HttpServer server;
    private final RouterConfig routerConfig;
    private final AppConfig appConfig;

    public VertxApplicationRunner(ApplicationContext applicationContext, AppConfig appConfig, DataSource dataSource) {
        this.appConfig = appConfig;
        DatabaseConfig.setDataSource(dataSource);

        this.vertx = Vertx.vertx();
        this.routerConfig = new RouterConfig(vertx, appConfig, applicationContext);
        this.server = vertx.createHttpServer()
            .requestHandler(routerConfig.build());
    }

    @Override
    public void run(ApplicationArguments args) {
        // 初始化数据库与全息日志表
        DatabaseConfig.init(appConfig.getDatabase());
        HolographicLogger.init(DatabaseConfig.getDataSource());

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
