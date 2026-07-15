package com.oneapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将旧版 {@link AppConfig} 纳入 Spring 容器管理。
 */
@Configuration
public class AppConfigConfiguration {

    @Bean
    public AppConfig appConfig() {
        return ConfigLoader.load();
    }
}
