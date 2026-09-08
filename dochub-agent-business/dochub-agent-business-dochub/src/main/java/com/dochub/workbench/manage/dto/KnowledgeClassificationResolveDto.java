package com.dochub.workbench.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KnowledgeClassificationResolveDto {
    @NotNull private Long reviewId;
    @NotNull private Integer version;
    @NotBlank private String mode;
    private String scopeCode;
    /** Blank means the explicit “无主题” choice. */
    private String topicCode;
}
