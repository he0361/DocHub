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
        baseUrl = stripTrailingSlashes(requireText(baseUrl, "baseUrl"));
        completionsPath = normalizePath(requireText(completionsPath, "completionsPath"));
        embeddingsPath = normalizePath(requireText(embeddingsPath, "embeddingsPath"));
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
        return value.trim();
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') end--;
        return value.substring(0, end);
    }

    private static String normalizePath(String value) {
        int start = 0;
        while (start < value.length() && value.charAt(start) == '/') start++;
        return '/' + value.substring(start);
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
