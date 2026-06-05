package com.oneapi;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.oneapi.config.AppConfig;
import com.oneapi.config.ConfigLoader;
import com.oneapi.config.DatabaseConfig;
import com.oneapi.config.RouterConfig;
import com.oneapi.service.RelayLogger;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        AppConfig config = ConfigLoader.load();
        int port = config.port();
        String sqlitePath = config.sqlitePath();

        // 初始化数据库
        DatabaseConfig.init(sqlitePath);

        // 初始化 relay-log.db（独立，静默失败）
        RelayLogger.init();

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

        // 优雅关闭
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            server.close();
            vertx.close();
        }));
    }
}
