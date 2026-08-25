package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 模板详情（含生成所需骨架与变量 schema）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocTemplateDetailVo {

    private Long templateId;

    private String templateCode;

    private String templateName;

    private String templateType;

    private String knowledgeScopeCode;

    private String description;

    private String outlinePrompt;

    private String contentTemplateText;

    /** JSON 数组字符串，描述 {{变量}} 的定义 */
    private String variableSchema;

    private String outputFormats;

    private Integer version;
}
