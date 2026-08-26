package com.dochub.workbench.modelconfig.vo;

/** Safe, API-key-free representation of an active model configuration. */
public record ModelConfigVo(Long id, Long configVersion, String deploymentType, String compatibilityPreset,
                            String baseUrl, String requestPath, String modelName, Double temperature,
                            Integer maxTokens, Integer timeoutMillis, boolean toolCallingSupported,
                            boolean hasApiKey, boolean active) {
}
