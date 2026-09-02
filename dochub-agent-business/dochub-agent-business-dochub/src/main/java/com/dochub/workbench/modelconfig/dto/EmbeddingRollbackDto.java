package com.dochub.workbench.modelconfig.dto;

import lombok.Data;

@Data
public class EmbeddingRollbackDto {
    private Long configVersion;
    private String currentPassword;
    private String confirmationPhrase;
}
