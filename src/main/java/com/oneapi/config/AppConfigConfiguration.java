package com.oneapi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

/**
 * 将旧版 {@link AppConfig} 纳入 Spring 容器管理，并把
 * {@link AppConfig.DatabaseYamlConfig} 装配为 Spring 的 {@link DataSource}，
 * 使得数据库连接配置（host / port / database / user / password）的单一来源
 * 是 {@code ~/.one-api/config.yaml}。
 */
@Configuration
public class AppConfigConfiguration {

    @Bean
    public AppConfig appConfig() {
        return ConfigLoader.load();
    }

    /**
     * 用 {@link AppConfig} 里的 database 段构造 DataSource，覆盖 application.yml 的默认值。
     * 仅在非 test profile 下激活，避免集成测试被强制要求 ~/.one-api/config.yaml。
     */
    @Bean
    @Primary
    @Profile("!test")
    public DataSource dataSource(AppConfig appConfig) {
        AppConfig.DatabaseYamlConfig db = appConfig.getDatabase();
        if (db == null) {
            throw new IllegalStateException(
                "config.yaml is missing the 'database:' section. " +
                "Add host/port/database/user/password under 'database:'.");
        }
        if (db.getPassword() == null || db.getPassword().isEmpty()) {
            throw new IllegalStateException(
                "config.yaml 'database.password' must not be empty. " +
                "Set it in " + System.getProperty("user.home") + "/.one-api/config.yaml");
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s",
            db.getHost(), db.getPort(), db.getDatabase()));
        hc.setUsername(db.getUser());
        hc.setPassword(db.getPassword());
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(600_000);
        hc.setMaxLifetime(1_800_000);
        hc.setKeepaliveTime(30_000);
        hc.setValidationTimeout(5_000);
        hc.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(hc);
    }
}
