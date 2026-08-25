package com.dochub.workbench.docgen.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 文档导出结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocExportResult {

    /** 下载文件名（含扩展名） */
    private String fileName;

    /** Content-Type */
    private String mediaType;

    private byte[] content;
}
