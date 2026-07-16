package com.oneapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 验证 Spring Boot 上下文能够完整加载。
 * 这是 Spring 化大迁移后的烟雾测试：所有 Bean（DataSource、JPA、Flyway、RouterConfig、
 * Vertx、VertxApplicationRunner、各 Service）必须能够成功装配。
 */
@SpringBootTest
@ActiveProfiles("test")
class ApplicationContextLoadTest {

    @Test
    void contextLoads() {
        // 如果 Spring 上下文加载失败，这个测试会自动失败。
    }
}
