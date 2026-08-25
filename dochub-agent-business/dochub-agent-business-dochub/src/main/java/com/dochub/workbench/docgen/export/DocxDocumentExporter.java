package com.dochub.workbench.docgen.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * DOCX 文档导出器：Markdown 经 AST 转换后写入 Word 文档。
 */
@Component
public class DocxDocumentExporter implements DocumentExporter {

    private final MarkdownToDocxConverter converter;

    public DocxDocumentExporter(MarkdownToDocxConverter converter) {
        this.converter = converter;
    }

    @Override
    public String format() {
        return "docx";
    }

    @Override
    public byte[] export(String markdown, String baseFileName) throws IOException {
        try (XWPFDocument document = converter.convert(markdown);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
