package com.dochub.workbench.modelconfig.dto;

import lombok.Data;

/** Candidate settings used for a non-persistent chat connection check. */
@Data
public class ModelConfigTestDto {
    private String deploymentType;
    private String compatibilityPreset;
    private String baseUrl;
    private String requestPath;
    private String modelName;
    private String apiKey;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutMillis;
    private Boolean toolCallingSupported;
}
