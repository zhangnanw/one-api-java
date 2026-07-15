package com.oneapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 统一入口。
 * <p>
 * 启动顺序：
 * 1. Spring 上下文加载（DataSource、JPA、Flyway、Cache、事务、Service）。
 * 2. {@link com.oneapi.core.VertxApplicationRunner} 在 Spring 启动完成后启动 Vert.x HTTP server。
 */
@SpringBootApplication
public class OneApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(OneApiApplication.class, args);
    }
}
