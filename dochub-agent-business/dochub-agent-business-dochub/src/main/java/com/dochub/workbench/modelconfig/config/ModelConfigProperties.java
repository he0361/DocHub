package com.dochub.workbench.modelconfig.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime model configuration properties (app.model-config.*).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.model-config")
public class ModelConfigProperties {

    /** Base64-encoded 32-byte AES key used to encrypt provider API keys. */
    private String encryptionKey;

    /** Model-name fragments unsuitable for the low-latency chat slot. */
    private java.util.List<String> reasoningOnlyPatterns = new java.util.ArrayList<>(java.util.List.of("qwq", "deepseek-r1", "thinking-only"));

    /**
     * Rejects a production process that cannot encrypt database-backed model credentials.
     * Development may start with YAML fallback only, before a runtime configuration is saved.
     */
    public void validate(boolean productionMode) {
        if (productionMode && (encryptionKey == null || encryptionKey.isBlank())) {
            throw new IllegalStateException("生产环境必须配置 DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
        }
    }
}
