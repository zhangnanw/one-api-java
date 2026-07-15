package com.oneapi.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要被操作日志切面记录的方法。
 * 适用于 Spring 管理的附加业务 Service/Controller。
 * <p>
 * 核心业务（Relay 主链路）不使用该注解，避免 AOP 代理影响性能。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {
}
