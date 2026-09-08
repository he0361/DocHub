package com.dochub.workbench.modelconfig.model;

import java.util.Objects;

/**
 * Decrypted, runtime-only model settings. Instances must never be logged or returned from an API.
 */
public record ModelRuntimeSpec(
    ModelType modelType,
    DeploymentType deploymentType,
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
        Objects.requireNonNull(deploymentType, "deploymentType must not be null");
        Objects.requireNonNull(compatibilityPreset, "compatibilityPreset must not be null");
        baseUrl = requireText(baseUrl, "baseUrl");
        completionsPath = requireText(completionsPath, "completionsPath");
        embeddingsPath = requireText(embeddingsPath, "embeddingsPath");
        apiKey = apiKey == null ? "" : apiKey;
        modelName = requireText(modelName, "modelName");
    }

    /** Compatibility constructor for callers whose legacy configuration is remote. */
    public ModelRuntimeSpec(ModelType modelType, CompatibilityPreset compatibilityPreset, String baseUrl,
                            String completionsPath, String embeddingsPath, String apiKey, String modelName,
                            Double temperature, Integer maxTokens, Integer timeoutMillis) {
        this(modelType, DeploymentType.REMOTE, compatibilityPreset, baseUrl, completionsPath, embeddingsPath,
            apiKey, modelName, temperature, maxTokens, timeoutMillis);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "ModelRuntimeSpec[modelType=" + modelType + ", deploymentType=" + deploymentType
            + ", compatibilityPreset=" + compatibilityPreset
            + ", baseUrl=" + baseUrl + ", completionsPath=" + completionsPath + ", embeddingsPath="
            + embeddingsPath + ", apiKey=<redacted>, modelName=" + modelName + ", temperature=" + temperature
            + ", maxTokens=" + maxTokens + ", timeoutMillis=" + timeoutMillis + ']';
    }
}
