package com.oneapi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 从 config.yaml 加载的类型化配置。无默认值 — 缺失即快速失败。
 */

@Setter
@Getter
public class AppConfig {

    @JsonProperty("server")
    private ServerConfig server;

    @JsonProperty("relay")
    private RelayConfig relay;

    @JsonProperty("policies")
    private PolicyConfig policies;

    @JsonProperty("database")
    private DatabaseYamlConfig database;

    public AppConfig() {}

    public int port() { return server != null ? server.getPort() : 13000; }

    /**
     * SQLite 数据库文件绝对路径。
     * 缺省值：~/.one-api/one-api.db。
     * 配置走 yaml 的 database.path；缺失则用缺省值。
     */
    public String sqlitePath() {
        if (database != null && database.getPath() != null && !database.getPath().isEmpty()) {
            return database.getPath();
        }
        return System.getProperty("user.home") + "/.one-api/one-api.db";
    }

    // --- 子配置 ---

    @Setter
    @Getter
    public static class DatabaseYamlConfig {
        private String path;

    }

    @Setter
    @Getter
    public static class ServerConfig {
        private int port = 13000;

    }

    @Setter
    @Getter
    public static class RelayConfig {
        private int maxRetries = 2;
        private int cacheTtlSeconds = 10;
        private List<String> layerOrder = List.of("free", "subscription", "payg");

    }

    @Setter
    @Getter
    public static class PolicyConfig {
        private ReasoningPolicyConfig reasoning = new ReasoningPolicyConfig();

        @Setter
        @Getter
        public static class ReasoningPolicyConfig {
            private String triggerSuffix = "-max";

        }
    }
}
