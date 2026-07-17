package com.oneapi.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 统一缓存配置：Caffeine 底层。
 * <p>
 * 容量与 TTL 通过 application.yml 的 spring.cache.caffeine.* 配置（如果有）。
 * 此处提供默认值：60 秒 TTL、500 条上限。
 * <p>
 * 业务 Service 直接在方法上加 {@link org.springframework.cache.annotation.Cacheable}
 * 即可生效，由本 Bean 统一管理底层。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(500));
        return manager;
    }
}