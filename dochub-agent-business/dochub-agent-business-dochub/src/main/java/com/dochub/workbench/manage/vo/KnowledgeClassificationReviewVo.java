package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class KnowledgeClassificationReviewVo {
    private Long reviewId;
    private Long documentId;
    private String documentName;
    private Integer profileVersion;
    private String reviewStatus;
    private String decisionType;
    private String proposedScopeJson;
    private String proposedTopicJson;
    private String candidateJson;
    private String evidenceJson;
    private String reason;
    private String selectedScopeCode;
    private String selectedTopicCode;
    private Integer trustLlm;
    private String operator;
    private Integer version;
    private Date createTime;
    private Date editTime;
}
