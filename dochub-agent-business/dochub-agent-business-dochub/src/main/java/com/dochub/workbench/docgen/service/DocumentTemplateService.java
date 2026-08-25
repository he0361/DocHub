package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dochub.workbench.docgen.constant.DocGenCode;
import com.dochub.workbench.docgen.data.DocTemplateEntity;
import com.dochub.workbench.docgen.dto.DocTemplateQueryDto;
import com.dochub.workbench.docgen.dto.DocTemplateSaveDto;
import com.dochub.workbench.docgen.mapper.DocTemplateMapper;
import com.dochub.workbench.docgen.vo.DocTemplateDetailVo;
import com.dochub.workbench.docgen.vo.DocTemplateItemVo;
import com.dochub.workbench.docgen.vo.DocTemplatePageVo;
import org.javaup.enums.BusinessStatus;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文枢 DocHub 文档模板服务：模板 CRUD 与分页查询。
 */
@Service
public class DocumentTemplateService {

    private final DocTemplateMapper templateMapper;
    private final UidGenerator uidGenerator;

    public DocumentTemplateService(DocTemplateMapper templateMapper, UidGenerator uidGenerator) {
        this.templateMapper = templateMapper;
        this.uidGenerator = uidGenerator;
    }

    public DocTemplatePageVo pageQuery(DocTemplateQueryDto dto) {
        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        LambdaQueryWrapper<DocTemplateEntity> wrapper = new LambdaQueryWrapper<DocTemplateEntity>()
            .eq(DocTemplateEntity::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DocTemplateEntity::getEditTime, DocTemplateEntity::getId);
        if (StrUtil.isNotBlank(dto.getKeyword())) {
            wrapper.and(query -> query.like(DocTemplateEntity::getTemplateName, dto.getKeyword().trim())
                .or().like(DocTemplateEntity::getTemplateCode, dto.getKeyword().trim()));
        }
        if (StrUtil.isNotBlank(dto.getTemplateType())) {
            wrapper.eq(DocTemplateEntity::getTemplateType, dto.getTemplateType());
        }
        IPage<DocTemplateEntity> page = templateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<DocTemplateItemVo> records = page.getRecords().stream().map(this::toItemVo).toList();
        return new DocTemplatePageVo(pageNo, pageSize, page.getTotal(), records);
    }

    public DocTemplateDetailVo detail(Long templateId) {
        DocTemplateEntity template = getTemplateOrThrow(templateId);
        return toDetailVo(template);
    }

    public Long save(DocTemplateSaveDto dto) {
        if (StrUtil.isBlank(dto.getContentTemplateText())) {
            throw new DochubFrameException(DocGenCode.GENERATION_FAILED.getCode(), "模板正文骨架不能为空");
        }
        if (dto.getTemplateId() != null) {
            DocTemplateEntity existing = getTemplateOrThrow(dto.getTemplateId());
            existing.setTemplateCode(StrUtil.blankToDefault(dto.getTemplateCode(), existing.getTemplateCode()));
            existing.setTemplateName(StrUtil.blankToDefault(dto.getTemplateName(), existing.getTemplateName()));
            existing.setTemplateType(StrUtil.blankToDefault(dto.getTemplateType(), existing.getTemplateType()));
            existing.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
            existing.setDescription(StrUtil.trimToNull(dto.getDescription()));
            existing.setOutlinePrompt(dto.getOutlinePrompt());
            existing.setContentTemplateText(dto.getContentTemplateText());
            existing.setVariableSchema(dto.getVariableSchema());
            existing.setOutputFormats(StrUtil.isBlank(dto.getOutputFormats()) ? "md,docx" : dto.getOutputFormats());
            existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
            templateMapper.updateById(existing);
            return existing.getId();
        }
        DocTemplateEntity template = new DocTemplateEntity();
        template.setId(uidGenerator.getUid());
        template.setTemplateCode(dto.getTemplateCode());
        template.setTemplateName(dto.getTemplateName());
        template.setTemplateType(StrUtil.blankToDefault(dto.getTemplateType(), "other"));
        template.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
        template.setDescription(StrUtil.trimToNull(dto.getDescription()));
        template.setOutlinePrompt(dto.getOutlinePrompt());
        template.setContentTemplateText(dto.getContentTemplateText());
        template.setVariableSchema(dto.getVariableSchema());
        template.setOutputFormats(StrUtil.isBlank(dto.getOutputFormats()) ? "md,docx" : dto.getOutputFormats());
        template.setVersion(1);
        template.setStatus(BusinessStatus.YES.getCode());
        templateMapper.insert(template);
        return template.getId();
    }

    public void delete(Long templateId) {
        DocTemplateEntity template = getTemplateOrThrow(templateId);
        template.setStatus(BusinessStatus.NO.getCode());
        templateMapper.updateById(template);
    }

    public DocTemplateEntity getTemplateOrThrow(Long templateId) {
        if (templateId == null) {
            throw new DochubFrameException(DocGenCode.TEMPLATE_NOT_FOUND.getCode(), "模板id不能为空");
        }
        DocTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null || !BusinessStatus.YES.getCode().equals(template.getStatus())) {
            throw new DochubFrameException(DocGenCode.TEMPLATE_NOT_FOUND.getCode(), "文档模板不存在: " + templateId);
        }
        return template;
    }

    private DocTemplateItemVo toItemVo(DocTemplateEntity template) {
        return new DocTemplateItemVo(template.getId(), template.getTemplateCode(), template.getTemplateName(),
            template.getTemplateType(), template.getKnowledgeScopeCode(), template.getDescription(), template.getVersion());
    }

    private DocTemplateDetailVo toDetailVo(DocTemplateEntity template) {
        return new DocTemplateDetailVo(template.getId(), template.getTemplateCode(), template.getTemplateName(),
            template.getTemplateType(), template.getKnowledgeScopeCode(), template.getDescription(),
            template.getOutlinePrompt(), template.getContentTemplateText(), template.getVariableSchema(),
            template.getOutputFormats(), template.getVersion());
    }
}
