package com.dochub.workbench.docgen.export;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Markdown 文档导出器：直接返回 UTF-8 文本。
 */
@Component
public class MarkdownDocumentExporter implements DocumentExporter {

    @Override
    public String format() {
        return "md";
    }

    @Override
    public byte[] export(String markdown, String baseFileName) throws IOException {
        return markdown == null ? new byte[0] : markdown.getBytes(StandardCharsets.UTF_8);
    }
}
