package com.dochub.workbench.modelconfig.vo;

import java.util.Date;

public record EmbeddingConfigVo(Long configVersion, String deploymentType, String compatibilityPreset,
                                String baseUrl, String requestPath, String modelName, Integer timeoutMillis,
                                boolean hasApiKey, int dimension, String documentCollection,
                                String memoryCollection, Long updatedBy, Date updateTime,
                                EmbeddingMigrationVo migration) { }
