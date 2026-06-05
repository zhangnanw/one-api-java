package com.oneapi.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Typed config loaded from config.yaml. No defaults — fail-fast on missing.
 */
public class AppConfig {

    @JsonProperty("server")
    private ServerConfig server;

    @JsonProperty("relay")
    private RelayConfig relay;

    @JsonProperty("policies")
    private PolicyConfig policies;

    public AppConfig() {}

    public ServerConfig getServer() { return server; }
    public void setServer(ServerConfig v) { this.server = v; }
    public RelayConfig getRelay() { return relay; }
    public void setRelay(RelayConfig relay) { this.relay = relay; }
    public PolicyConfig getPolicies() { return policies; }
    public void setPolicies(PolicyConfig policies) { this.policies = policies; }

    public int port() { return server != null ? server.getPort() : 13000; }
    public String sqlitePath() { return System.getProperty("user.home") + "/.one-api/one-api.db"; }

    // --- Sub-configs ---

    public static class ServerConfig {
        private int port = 13000;
        public int getPort() { return port; }
        public void setPort(int v) { this.port = v; }
    }

    public static class RelayConfig {
        private int maxRetries = 2;
        private int cacheTtlSeconds = 10;
        private List<String> layerOrder = List.of("free", "subscription", "payg");

        public int getMaxRetries() { return maxRetries; }
        public void setMaxRetries(int v) { this.maxRetries = v; }
        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public void setCacheTtlSeconds(int v) { this.cacheTtlSeconds = v; }
        public List<String> getLayerOrder() { return layerOrder; }
        public void setLayerOrder(List<String> v) { this.layerOrder = v; }
    }

    public static class PolicyConfig {
        private ReasoningPolicyConfig reasoning = new ReasoningPolicyConfig();

        public ReasoningPolicyConfig getReasoning() { return reasoning; }
        public void setReasoning(ReasoningPolicyConfig v) { this.reasoning = v; }

        public static class ReasoningPolicyConfig {
            private String triggerSuffix = "-max";

            public String getTriggerSuffix() { return triggerSuffix; }
            public void setTriggerSuffix(String v) { this.triggerSuffix = v; }
        }
    }
}
