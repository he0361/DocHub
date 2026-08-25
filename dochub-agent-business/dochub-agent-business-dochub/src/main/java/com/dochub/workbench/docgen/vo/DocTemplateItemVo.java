package com.dochub.workbench.docgen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 模板列表项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocTemplateItemVo {

    private Long templateId;

    private String templateCode;

    private String templateName;

    private String templateType;

    private String knowledgeScopeCode;

    private String description;

    private Integer version;
}
