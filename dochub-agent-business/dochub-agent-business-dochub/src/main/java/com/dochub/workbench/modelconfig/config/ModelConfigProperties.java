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
}
