package com.dochub.workbench.docgen.export;

import java.io.IOException;

/**
 * 文枢 DocHub 文档导出器接口。
 *
 * <p>新格式（如 PDF）只需新增实现并注册为 Spring Bean，即可被 DocumentExportService 自动识别。</p>
 */
public interface DocumentExporter {

    /** 导出格式标识，如 md / docx / pdf */
    String format();

    byte[] export(String markdown, String baseFileName) throws IOException;
}
