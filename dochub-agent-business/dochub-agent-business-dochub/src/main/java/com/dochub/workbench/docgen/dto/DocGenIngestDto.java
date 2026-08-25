package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 生成文档一键入库 DTO。
 */
@Data
public class DocGenIngestDto {

    private String recordCode;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String businessCategory;

    private String documentTags;
}
