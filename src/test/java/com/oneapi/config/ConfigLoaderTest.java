package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ConfigLoader and classpath config parsing.
 * Requires config.yaml in test resources (copy from ~/.one-api/).
 */
class ConfigLoaderTest {
    private static final ObjectMapper yaml = new ObjectMapper(new YAMLFactory());

    private InputStream load(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    @Test
    void configYaml_exists() {
        assertThat(load("config.yaml"))
            .as("config.yaml must exist in test resources").isNotNull();
    }

    @Test
    void configYaml_parsesCorrectly() throws Exception {
        AppConfig config = yaml.readValue(load("config.yaml"), AppConfig.class);
        assertThat(config).isNotNull();
        assertThat(config.getRelay()).isNotNull();
    }

    @Test
    void configYaml_hasRelaySection() throws Exception {
        AppConfig config = yaml.readValue(load("config.yaml"), AppConfig.class);

        assertThat(config.getRelay().getMaxRetries()).isPositive();
        assertThat(config.getRelay().getCacheTtlSeconds()).isPositive();
        assertThat(config.getRelay().getLayerOrder()).isNotEmpty();
    }

    @Test
    void configYaml_serverPort() throws Exception {
        AppConfig config = yaml.readValue(load("config.yaml"), AppConfig.class);

        assertThat(config.port()).isEqualTo(13000);
    }

    @Test
    void configYaml_policiesSection() throws Exception {
        AppConfig config = yaml.readValue(load("config.yaml"), AppConfig.class);

        assertThat(config.getPolicies()).isNotNull();
        assertThat(config.getPolicies().getReasoning()).isNotNull();
        assertThat(config.getPolicies().getReasoning().getTriggerSuffix()).isNotNull();
    }
}
