package com.oneapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

/**
 * 从 ~/.one-api/ 或 classpath 加载 config.yaml。
 * 配置缺失或格式错误时快速失败。
 */
@Slf4j
public class ConfigLoader {
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public static AppConfig load() {
        // 1. 尝试 ~/.one-api/config.yaml
        File homeConfig = new File(System.getProperty("user.home"), ".one-api/config.yaml");
        if (homeConfig.exists()) {
            log.info("Loading config from {}", homeConfig.getAbsolutePath());
            return parse(homeConfig);
        }

        // 2. 回退：classpath 中的 config.yaml
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

    // 端口统一从 AppConfig 取值，Main.java 用 config.port()
    private static AppConfig parse(File file) {
        try {
            AppConfig config = yamlMapper.readValue(file, AppConfig.class);
            if (config.getRelay() == null) {
                throw new RuntimeException("config.yaml missing 'relay:' section");
            }
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
