package com.dochub.workbench.docgen.dto;

import lombok.Data;

import java.util.Map;

/**
 * 文枢 DocHub 文档生成请求 DTO。
 */
@Data
public class DocGenGenerateDto {

    private Long templateId;

    /** 用户填写的模板变量，键与模板 {{变量}} 一一对应 */
    private Map<String, String> variables;

    /** 一句话需求，可留空由模板默认生成 */
    private String requirement;
}
