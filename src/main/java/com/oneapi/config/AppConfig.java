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


    // --- 子配置 ---

    @Setter
    @Getter
    public static class DatabaseYamlConfig {
        // PostgreSQL 配置
        private String host = "localhost";
        private int port = 5432;
        private String database = "oneapi";
        private String user = "oneapi";
        private String password;

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
