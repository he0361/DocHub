package com.dochub.workbench.modelconfig.vo;

public record EmbeddingModelChangeVo(String status, Long configVersion, Long migrationId, String message) { }
