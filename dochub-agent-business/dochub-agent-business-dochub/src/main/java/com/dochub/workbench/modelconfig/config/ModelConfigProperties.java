package com.dochub.workbench.modelconfig.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Runtime model configuration properties (app.model-config.*).
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.model-config")
public class ModelConfigProperties {

    private static final int KEY_BYTES = 32;
    private static final String KEY_DIR = ".dochub";
    private static final String KEY_FILE = "model-config-encryption.key";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Base64-encoded 32-byte AES key used to encrypt provider API keys. */
    private String encryptionKey;

    /** Model-name fragments unsuitable for the low-latency chat slot. */
    private java.util.List<String> reasoningOnlyPatterns = new java.util.ArrayList<>(java.util.List.of("qwq", "deepseek-r1", "thinking-only"));

    /**
     * Rejects a production process that cannot encrypt database-backed model credentials.
     * In development, a blank key is transparently provisioned once and persisted under
     * {@code <user.home>/.dochub/model-config-encryption.key} so a restart reuses it and can
     * still decrypt previously saved remote credentials.
     */
    public void validate(boolean productionMode) {
        if (productionMode && (encryptionKey == null || encryptionKey.isBlank())) {
            throw new IllegalStateException("生产环境必须配置 DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
        }
        if (!productionMode) {
            ensureDevelopmentKey();
        }
    }

    private void ensureDevelopmentKey() {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            return;
        }
        Path keyFile = keyFile();
        String existing = readValidKey(keyFile);
        if (existing != null) {
            encryptionKey = existing;
            return;
        }
        String generated = generateKey();
        try {
            Files.createDirectories(keyFile.getParent());
            try {
                Files.writeString(keyFile, generated, StandardCharsets.US_ASCII,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            } catch (FileAlreadyExistsException concurrentProvision) {
                String raced = readValidKey(keyFile);
                if (raced != null) {
                    encryptionKey = raced;
                    return;
                }
                Files.writeString(keyFile, generated, StandardCharsets.US_ASCII,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
        } catch (IOException ioFailure) {
            // Best-effort: keep the in-memory key so the local process can still encrypt.
        }
        encryptionKey = generated;
    }

    private Path keyFile() {
        return Path.of(System.getProperty("user.home"), KEY_DIR, KEY_FILE);
    }

    private String readValidKey(Path keyFile) {
        try {
            if (Files.exists(keyFile)) {
                String content = Files.readString(keyFile).trim();
                if (isValidKey(content)) {
                    return content;
                }
            }
        } catch (IOException ignored) {
            // Fall through to regeneration below.
        }
        return null;
    }

    private boolean isValidKey(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(content).length == KEY_BYTES;
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
    }

    private String generateKey() {
        byte[] bytes = new byte[KEY_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
