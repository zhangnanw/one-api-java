package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    static final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    @Test
    void parseRelayConfig() throws Exception {
        String y = """
            relay:
              maxRetries: 3
              cacheTtlSeconds: 15
              layerOrder:
                - subscription
                - free
                - payg
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);

        assertThat(config.getRelay()).isNotNull();
        assertThat(config.getRelay().getMaxRetries()).isEqualTo(3);
        assertThat(config.getRelay().getCacheTtlSeconds()).isEqualTo(15);
        assertThat(config.getRelay().getLayerOrder())
            .containsExactly("subscription", "free", "payg");
    }

    @Test
    void parseServerConfig() throws Exception {
        String y = """
            server:
              port: 8080
            relay:
              maxRetries: 1
              cacheTtlSeconds: 5
              layerOrder:
                - free
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);

        assertThat(config.port()).isEqualTo(8080);
    }

    @Test
    void missingRelay_returnsNull() throws Exception {
        String y = """
            server:
              port: 8080
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);

        assertThat(config.getRelay()).isNull();
    }

    @Test
    void defaultValues() throws Exception {
        String y = """
            relay:
              maxRetries: 0
              cacheTtlSeconds: 0
              layerOrder: []
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);

        assertThat(config.getRelay().getMaxRetries()).isEqualTo(0);
        // port defaults to 13000 when server section is missing
        assertThat(config.port()).isEqualTo(13000);
    }

    @Test
    void relayConfig_layerOrder_empty() throws Exception {
        String y = """
            relay:
              maxRetries: 1
              cacheTtlSeconds: 5
              layerOrder: []
            """;
        AppConfig config = yaml.readValue(y, AppConfig.class);

        assertThat(config.getRelay().getLayerOrder()).isEmpty();
    }
}
