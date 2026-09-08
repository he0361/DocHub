package com.dochub.workbench.modelconfig.dto;

import lombok.Data;

@Data
public class EmbeddingModelChangeDto {
    private String deploymentType;
    private String compatibilityPreset;
    private String baseUrl;
    private String requestPath;
    private String modelName;
    private String apiKey;
    private Boolean clearApiKey;
    private Integer timeoutMillis;
    private String currentPassword;
    private String confirmationPhrase;
}
