package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 显式声明 ObjectMapper Bean，确保业务代码中统一使用 Spring 管理的 Jackson 实例。
 * <p>
 * Spring Boot 本身也会自动配置 ObjectMapper；此处显式定义是为了让所有依赖注入点
 * 引用同一个实例，避免各处的 {@code new ObjectMapper()} 分散创建导致序列化行为不一致。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
