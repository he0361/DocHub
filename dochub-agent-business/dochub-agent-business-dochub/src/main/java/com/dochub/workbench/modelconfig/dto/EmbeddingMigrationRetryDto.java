package com.dochub.workbench.modelconfig.dto;

import lombok.Data;

@Data
public class EmbeddingMigrationRetryDto {
    private Long migrationId;
    private String currentPassword;
    private String confirmationPhrase;
}
