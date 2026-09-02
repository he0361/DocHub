package com.dochub.workbench.manage.dto;

import lombok.Data;

@Data
public class KnowledgeClassificationReviewQueryDto {
    private Long reviewId;
    private Long documentId;
    private String reviewStatus;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
