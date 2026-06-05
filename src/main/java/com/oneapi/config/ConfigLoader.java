package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * Load config.yaml from ~/.one-api/ or classpath.
 * Fail-fast on missing or malformed config.
 */
public class ConfigLoader {
    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static AppConfig load() {
        // 1. Try ~/.one-api/config.yaml
        File homeConfig = new File(System.getProperty("user.home"), ".one-api/config.yaml");
        if (homeConfig.exists()) {
            log.info("Loading config from {}", homeConfig.getAbsolutePath());
            return parse(homeConfig);
        }

        // 2. Fallback: classpath config.yaml
        var is = ConfigLoader.class.getClassLoader().getResourceAsStream("config.yaml");
        if (is != null) {
            log.info("Loading config from classpath");
            try {
                return yamlMapper.readValue(is, AppConfig.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to parse classpath config.yaml", e);
            }
        }

        throw new RuntimeException(
            "config.yaml not found. Place it at " + homeConfig.getAbsolutePath() +
            " or in classpath.");
    }

    /** Default HTTP listen port. */
    public static int port() {
        return 3000;
    }

    /** Default SQLite database path. */
    public static String sqlitePath() {
        return System.getProperty("user.home") + "/.one-api/one-api.db";
    }

    private static AppConfig parse(File file) {
        try {
            AppConfig config = yamlMapper.readValue(file, AppConfig.class);
            log.info("Config loaded: maxRetries={}, cacheTtl={}, layerOrder={}",
                config.getRelay().getMaxRetries(),
                config.getRelay().getCacheTtlSeconds(),
                config.getRelay().getLayerOrder());
            return config;
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse " + file.getAbsolutePath(), e);
        }
    }
}
