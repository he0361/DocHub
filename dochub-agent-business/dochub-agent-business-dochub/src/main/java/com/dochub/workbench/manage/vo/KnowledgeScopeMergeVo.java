package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class KnowledgeScopeMergeVo {
    private String sourceScopeCode;
    private String targetScopeCode;
    private Integer documentCount;
    private Integer topicCount;
    private Integer relationCount;
}
