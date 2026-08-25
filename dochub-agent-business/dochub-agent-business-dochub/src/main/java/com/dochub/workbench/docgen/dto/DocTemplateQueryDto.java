package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 模板分页查询 DTO。
 */
@Data
public class DocTemplateQueryDto {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private String templateType;
}
