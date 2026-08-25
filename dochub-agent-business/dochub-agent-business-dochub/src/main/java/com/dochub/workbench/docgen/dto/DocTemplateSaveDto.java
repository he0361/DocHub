package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 模板新增/保存 DTO。
 */
@Data
public class DocTemplateSaveDto {

    private Long templateId;

    private String templateCode;

    private String templateName;

    private String templateType;

    private String knowledgeScopeCode;

    private String description;

    private String outlinePrompt;

    private String contentTemplateText;

    private String variableSchema;

    private String outputFormats;
}
