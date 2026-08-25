package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.docgen.constant.DocGenCode;
import com.dochub.workbench.docgen.data.DocGenerationRecordEntity;
import com.dochub.workbench.docgen.export.DocumentExporter;
import com.dochub.workbench.docgen.mapper.DocGenerationRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文枢 DocHub 文档导出服务。
 *
 * <p>按生成记录 + 格式选择导出器（md/docx，未来可扩展 pdf），统一返回文件名/类型/字节。</p>
 */
@Slf4j
@Service
public class DocumentExportService {

    private final List<DocumentExporter> exporters;
    private final DocGenerationRecordMapper recordMapper;

    public DocumentExportService(List<DocumentExporter> exporters, DocGenerationRecordMapper recordMapper) {
        this.exporters = exporters;
        this.recordMapper = recordMapper;
    }

    public DocExportResult export(String recordCode, String format) {
        if (StrUtil.isBlank(recordCode)) {
            throw new DochubFrameException(DocGenCode.RECORD_NOT_FOUND.getCode(), "生成记录编号不能为空");
        }
        DocGenerationRecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<DocGenerationRecordEntity>()
            .eq(DocGenerationRecordEntity::getRecordCode, recordCode)
            .last("LIMIT 1"));
        if (record == null) {
            throw new DochubFrameException(DocGenCode.RECORD_NOT_FOUND.getCode(), "生成记录不存在: " + recordCode);
        }
        if (StrUtil.isBlank(record.getGeneratedMarkdown())) {
            throw new DochubFrameException(DocGenCode.RECORD_CONTENT_EMPTY.getCode(), "该生成记录没有可导出的正文内容");
        }
        String targetFormat = StrUtil.isNotBlank(format) ? format.trim().toLowerCase() : "md";
        DocumentExporter exporter = exporters.stream()
            .filter(item -> item.format().equalsIgnoreCase(targetFormat))
            .findFirst()
            .orElseThrow(() -> new DochubFrameException(DocGenCode.EXPORT_FORMAT_UNSUPPORTED.getCode(),
                "不支持的导出格式: " + targetFormat));

        String baseName = resolveBaseName(record);
        String fileName = baseName + "." + exporter.format();
        try {
            byte[] content = exporter.export(record.getGeneratedMarkdown(), baseName);
            return new DocExportResult(fileName, resolveMediaType(exporter.format()), content);
        }
        catch (Exception exception) {
            log.error("导出文档失败: recordCode={}, format={}", recordCode, targetFormat, exception);
            throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(),
                "导出文档失败: " + exception.getMessage(), exception);
        }
    }

    private String resolveBaseName(DocGenerationRecordEntity record) {
        if (StrUtil.isNotBlank(record.getFileName())) {
            String name = record.getFileName();
            int dotIndex = name.lastIndexOf('.');
            return dotIndex > 0 ? name.substring(0, dotIndex) : name;
        }
        return StrUtil.isNotBlank(record.getTemplateName()) ? record.getTemplateName() : "文枢生成文档";
    }

    private String resolveMediaType(String format) {
        return switch (format.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "text/markdown;charset=UTF-8";
        };
    }
}
