package com.dochub.workbench.modelconfig.model;

import java.util.Objects;

/**
 * Decrypted, runtime-only model settings. Instances must never be logged or returned from an API.
 */
public record ModelRuntimeSpec(
    ModelType modelType,
    CompatibilityPreset compatibilityPreset,
    String baseUrl,
    String completionsPath,
    String embeddingsPath,
    String apiKey,
    String modelName,
    Double temperature,
    Integer maxTokens,
    Integer timeoutMillis
) {

    public ModelRuntimeSpec {
        Objects.requireNonNull(modelType, "modelType must not be null");
        Objects.requireNonNull(compatibilityPreset, "compatibilityPreset must not be null");
        baseUrl = requireText(baseUrl, "baseUrl");
        completionsPath = requireText(completionsPath, "completionsPath");
        embeddingsPath = requireText(embeddingsPath, "embeddingsPath");
        apiKey = apiKey == null ? "" : apiKey;
        modelName = requireText(modelName, "modelName");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
