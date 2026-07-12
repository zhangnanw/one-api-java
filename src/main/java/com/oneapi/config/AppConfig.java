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

    /**
     * 中继行为配置。
     * <ul>
     *   <li>{@link #requireVirtualModel} = true 时，请求物理模型名（出现在 instances.model_name 但未注册到 virtual_models）
     *       直接报 404（DirectUseForbidden）；这是 README §"API 表面只暴露虚拟模型"的语义化开关。</li>
     * </ul>
     */
    @Setter
    @Getter
    public static class RelayConfig {
        private int maxRetries = 2;
        private int cacheTtlSeconds = 10;
        private List<String> layerOrder = List.of("free", "subscription", "payg");
        private boolean requireVirtualModel = true;

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
