package com.dochub.workbench.manage.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeScopeMergeDto {
    @NotBlank
    private String sourceScopeCode;
    @NotBlank
    private String targetScopeCode;
}
