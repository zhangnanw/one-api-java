package com.oneapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 启用 Spring 声明式事务。
 * <p>
 * Step 5 接入数据源后启用，为后续 JPA / JDBC 操作提供 @Transactional 支持。
 * 核心业务（Vert.x Relay）不使用该事务，避免阻塞事件循环。
 */
@Configuration
@EnableTransactionManagement
public class SpringTransactionConfig {
}
