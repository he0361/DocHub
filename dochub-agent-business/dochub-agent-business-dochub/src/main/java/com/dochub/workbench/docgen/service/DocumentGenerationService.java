package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dochub.workbench.docgen.constant.DocGenCode;
import com.dochub.workbench.docgen.data.DocGenerationRecordEntity;
import com.dochub.workbench.docgen.data.DocTemplateEntity;
import com.dochub.workbench.docgen.dto.DocGenGenerateDto;
import com.dochub.workbench.docgen.dto.DocGenIngestDto;
import com.dochub.workbench.docgen.dto.DocGenRecordQueryDto;
import com.dochub.workbench.docgen.mapper.DocGenerationRecordMapper;
import com.dochub.workbench.docgen.support.TemplateVariableResolver;
import com.dochub.workbench.docgen.vo.DocGenGenerateVo;
import com.dochub.workbench.docgen.vo.DocGenIngestVo;
import com.dochub.workbench.docgen.vo.DocGenRecordItemVo;
import com.dochub.workbench.docgen.vo.DocGenRecordPageVo;
import com.dochub.workbench.manage.dto.DocumentUploadDto;
import com.dochub.workbench.manage.service.DocumentManageService;
import com.dochub.workbench.manage.vo.DocumentUploadVo;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文枢 DocHub 文档生成编排服务。
 *
 * <p>生成主流程：选模板 → 大纲规划（LLM，失败回退模板自带大纲）→ 正文生成（LLM 直接产 Markdown）
 * → 落生成历史记录 → 支持导出（md/docx）与一键入库。</p>
 */
@Slf4j
@Service
public class DocumentGenerationService {

    /** 生成成功 */
    public static final int STATUS_SUCCESS = 2;
    /** 生成失败 */
    public static final int STATUS_FAILED = 3;

    private final DocumentTemplateService templateService;
    private final DocGenerationRecordMapper recordMapper;
    private final DocumentOutlinePlanner outlinePlanner;
    private final DocumentBodyGenerator bodyGenerator;
    private final DocumentExportService exportService;
    private final DocumentManageService documentManageService;
    private final TemplateVariableResolver templateVariableResolver;
    private final UidGenerator uidGenerator;

    public DocumentGenerationService(DocumentTemplateService templateService,
                                     DocGenerationRecordMapper recordMapper,
                                     DocumentOutlinePlanner outlinePlanner,
                                     DocumentBodyGenerator bodyGenerator,
                                     DocumentExportService exportService,
                                     DocumentManageService documentManageService,
                                     TemplateVariableResolver templateVariableResolver,
                                     UidGenerator uidGenerator) {
        this.templateService = templateService;
        this.recordMapper = recordMapper;
        this.outlinePlanner = outlinePlanner;
        this.bodyGenerator = bodyGenerator;
        this.exportService = exportService;
        this.documentManageService = documentManageService;
        this.templateVariableResolver = templateVariableResolver;
        this.uidGenerator = uidGenerator;
    }

    public DocGenGenerateVo generate(DocGenGenerateDto dto) {
        DocTemplateEntity template = templateService.getTemplateOrThrow(dto.getTemplateId());
        if (template == null) {
            throw new DochubFrameException(DocGenCode.TEMPLATE_NOT_FOUND.getCode(), "文档模板不存在");
        }
        Map<String, String> variables = dto.getVariables() == null ? Map.of() : dto.getVariables();
        String requirement = StrUtil.blankToDefault(dto.getRequirement(), "");

        // 1. 大纲：LLM 规划，失败回退模板自带大纲
        List<String> outline = outlinePlanner.planOutline(template, requirement, variables);
        if (outline.isEmpty()) {
            outline = extractHeadings(templateVariableResolver.resolve(template.getContentTemplateText(), variables));
        }

        // 2. 正文：LLM 直接产出 Markdown
        long startMillis = System.currentTimeMillis();
        String body = bodyGenerator.generateBody(template, requirement, variables, outline);
        long costMillis = System.currentTimeMillis() - startMillis;

        // 3. 落生成历史记录
        DocGenerationRecordEntity record = buildRecord(template, variables, requirement, outline);
        record.setGeneratedMarkdown(body);
        record.setCostMillis(costMillis);
        if (StrUtil.isBlank(body)) {
            record.setGenerationStatus(STATUS_FAILED);
            record.setErrorMsg("模型生成正文为空或超时");
            recordMapper.insert(record);
            log.warn("文档生成失败，正文为空: templateId={}", template.getId());
            throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(), "文档生成失败：模型返回正文为空或超时");
        }
        record.setGenerationStatus(STATUS_SUCCESS);
        recordMapper.insert(record);

        return new DocGenGenerateVo(record.getRecordCode(), template.getTemplateName(),
            template.getTemplateName() + ".md", outline, body, STATUS_SUCCESS);
    }

    public DocExportResult export(String recordCode, String format) {
        return exportService.export(recordCode, format);
    }

    public DocGenRecordPageVo pageRecords(DocGenRecordQueryDto dto) {
        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        LambdaQueryWrapper<DocGenerationRecordEntity> wrapper = new LambdaQueryWrapper<DocGenerationRecordEntity>()
            .eq(DocGenerationRecordEntity::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DocGenerationRecordEntity::getCreateTime, DocGenerationRecordEntity::getId);
        if (dto.getTemplateId() != null) {
            wrapper.eq(DocGenerationRecordEntity::getTemplateId, dto.getTemplateId());
        }
        if (dto.getGenerationStatus() != null) {
            wrapper.eq(DocGenerationRecordEntity::getGenerationStatus, dto.getGenerationStatus());
        }
        IPage<DocGenerationRecordEntity> page = recordMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<DocGenRecordItemVo> records = page.getRecords().stream().map(this::toRecordItemVo).toList();
        return new DocGenRecordPageVo(pageNo, pageSize, page.getTotal(), records);
    }

    /**
     * 删除一条生成历史记录。
     */
    public void deleteRecord(String recordCode) {
        if (StrUtil.isBlank(recordCode)) {
            throw new DochubFrameException(DocGenCode.RECORD_NOT_FOUND.getCode(), "生成记录编号不能为空");
        }
        int deleted = recordMapper.delete(new LambdaQueryWrapper<DocGenerationRecordEntity>()
            .eq(DocGenerationRecordEntity::getRecordCode, recordCode));
        if (deleted == 0) {
            throw new DochubFrameException(DocGenCode.RECORD_NOT_FOUND.getCode(), "生成记录不存在");
        }
    }

    public DocGenIngestVo ingest(DocGenIngestDto dto) {
        if (StrUtil.isBlank(dto.getRecordCode())) {
            throw new DochubFrameException(DocGenCode.RECORD_NOT_FOUND.getCode(), "生成记录编号不能为空");
        }
        DocGenerationRecordEntity record = recordMapper.selectOne(new LambdaQueryWrapper<DocGenerationRecordEntity>()
            .eq(DocGenerationRecordEntity::getRecordCode, dto.getRecordCode())
            .last("LIMIT 1"));
        if (record == null || StrUtil.isBlank(record.getGeneratedMarkdown())) {
            throw new DochubFrameException(DocGenCode.RECORD_CONTENT_EMPTY.getCode(), "生成记录不存在或没有可入库正文");
        }
        DocumentUploadDto uploadDto = new DocumentUploadDto();
        String name = StrUtil.isNotBlank(record.getTemplateName()) ? record.getTemplateName() : record.getRecordCode();
        uploadDto.setDocumentName(name);
        uploadDto.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
        uploadDto.setKnowledgeScopeName(StrUtil.trimToNull(dto.getKnowledgeScopeName()));
        uploadDto.setBusinessCategory(StrUtil.trimToNull(dto.getBusinessCategory()));
        uploadDto.setDocumentTags(StrUtil.trimToNull(dto.getDocumentTags()));
        DocumentUploadVo uploadVo = documentManageService.ingestGeneratedText(name, record.getGeneratedMarkdown(), uploadDto);

        // 回填一键入库后的文档 id，形成"生成 → 入库 → 检索"闭环
        record.setSourceDocumentId(uploadVo.getDocumentId());
        recordMapper.updateById(record);
        return new DocGenIngestVo(uploadVo.getDocumentId(), uploadVo.getTaskId(), uploadVo.getDocumentName());
    }

    private DocGenerationRecordEntity buildRecord(DocTemplateEntity template, Map<String, String> variables,
                                                  String requirement, List<String> outline) {
        DocGenerationRecordEntity record = new DocGenerationRecordEntity();
        record.setId(uidGenerator.getUid());
        record.setRecordCode("DG" + uidGenerator.getUid());
        record.setTemplateId(template.getId());
        record.setTemplateName(template.getTemplateName());
        record.setGenerationMode("TEMPLATE_GUIDED");
        record.setUserRequirement(StrUtil.isBlank(requirement) ? null : requirement);
        record.setVariablesJson(variables.isEmpty() ? null : toJson(variables));
        record.setOutputFormat("md");
        record.setFileName(template.getTemplateName() + ".md");
        record.setGenerationStatus(STATUS_SUCCESS);
        record.setStatus(BusinessStatus.YES.getCode());
        return record;
    }

    private String toJson(Map<String, String> variables) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(variables);
        }
        catch (Exception exception) {
            return null;
        }
    }

    private List<String> extractHeadings(String markdown) {
        List<String> headings = new ArrayList<>();
        if (StrUtil.isBlank(markdown)) {
            return headings;
        }
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^#{1,6}\\s+.+")) {
                headings.add(trimmed.replaceFirst("^#{1,6}\\s+", ""));
            }
        }
        return headings;
    }

    private DocGenRecordItemVo toRecordItemVo(DocGenerationRecordEntity record) {
        return new DocGenRecordItemVo(record.getRecordCode(), record.getTemplateId(), record.getTemplateName(),
            record.getUserRequirement(), record.getFileName(), record.getGenerationStatus(), record.getErrorMsg(),
            record.getModelName(), record.getPromptTokens(), record.getCompletionTokens(), record.getCostMillis(),
            record.getSourceDocumentId(), record.getCreateTime());
    }
}
