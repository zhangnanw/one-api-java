package com.oneapi;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import lombok.extern.slf4j.Slf4j;

import com.oneapi.config.AppConfig;
import com.oneapi.config.ConfigLoader;
import com.oneapi.config.DatabaseConfig;
import com.oneapi.config.RouterConfig;
import com.oneapi.config.SchemaManager;
import com.oneapi.background.HolographicLogger;
import com.oneapi.core.RelayLogger;

@Slf4j
public class Main {

    public static void main(String[] args) {
        AppConfig config = ConfigLoader.load();
        int port = config.port();

        // 初始化数据库（PostgreSQL）
        DatabaseConfig.init(config.getDatabase());

        // 确保表结构支持自增 id
        new SchemaManager(DatabaseConfig.getDataSource()).migrate();

        // 初始化日志 DB（复用主 DataSource）
        RelayLogger.init(DatabaseConfig.getDataSource());
        HolographicLogger.init(DatabaseConfig.getDataSource());

        // 创建 Vert.x
        Vertx vertx = Vertx.vertx();

        // 构建路由
        var routerConfig = new RouterConfig(vertx, config);
        var router = routerConfig.build();

        // 启动 HTTP 服务器
        HttpServer server = vertx.createHttpServer()
            .requestHandler(router)
            .listen(port, ar -> {
                if (ar.succeeded()) {
                    log.info("one-api-java started on http://localhost:{}", port);
                } else {
                    log.error("Failed to start: {}", ar.cause().getMessage());
                    System.exit(1);
                }
            });

        // 优雅关闭 — 顺序 await 每一步，确保在途请求 drain 完再进入下一步
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            // 1. 停止接受新连接，等在途请求完成
            server.close().toCompletionStage().toCompletableFuture().join();
            // 2. 关闭内部资源（上游客户端、retention scheduler）
            routerConfig.close();
            // 3. 最后关 Vert.x（worker pool、event loop）
            vertx.close().toCompletionStage().toCompletableFuture().join();
            log.info("Shutdown complete.");
        }));
    }
}
