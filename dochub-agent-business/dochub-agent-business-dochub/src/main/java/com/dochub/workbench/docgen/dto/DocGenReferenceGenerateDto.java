package com.dochub.workbench.docgen.dto;

import lombok.Data;

/**
 * 文枢 DocHub 参考文档仿写请求 DTO。
 *
 * <p>参考来源二选一：上传文件（multipart 的 file）或知识库已有文档（referenceDocumentId）。</p>
 */
@Data
public class DocGenReferenceGenerateDto {

    /** 知识库已有文档 id（与上传文件二选一） */
    private Long referenceDocumentId;

    /** 用户需求：希望仿照参考文档生成什么文档 */
    private String requirement;
}
