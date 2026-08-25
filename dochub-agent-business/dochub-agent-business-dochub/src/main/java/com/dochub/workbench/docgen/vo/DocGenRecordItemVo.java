package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文枢 DocHub 生成历史项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocGenRecordItemVo {

    private String recordCode;

    private Long templateId;

    private String templateName;

    private String userRequirement;

    private String fileName;

    private Integer generationStatus;

    private String errorMsg;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long costMillis;

    private Long sourceDocumentId;

    private Date createTime;
}
