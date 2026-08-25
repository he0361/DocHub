package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 生成历史分页查询 DTO。
 */
@Data
public class DocGenRecordQueryDto {

    private Integer pageNo;

    private Integer pageSize;

    private Long templateId;

    private Integer generationStatus;
}
