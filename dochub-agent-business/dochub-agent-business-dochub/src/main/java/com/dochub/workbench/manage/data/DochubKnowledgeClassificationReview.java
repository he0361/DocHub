package com.dochub.workbench.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dochub_knowledge_classification_review")
public class DochubKnowledgeClassificationReview extends BaseTableData {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long documentId;
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
}
