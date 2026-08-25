package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 文档导出请求 DTO。
 */
@Data
public class DocGenExportDto {

    private String recordCode;

    /** 导出格式：md / docx */
    private String format;
}
