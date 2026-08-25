package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubDocumentProfile;
import com.dochub.workbench.manage.data.DochubDocumentStructureNode;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentProfileMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentStructureNodeMapper;
import com.dochub.workbench.manage.model.KnowledgeClassifyResult;
import com.dochub.workbench.manage.service.DocumentProfileService;
import com.dochub.workbench.manage.service.DocumentStorageService;
import com.dochub.workbench.manage.service.KnowledgeScopeClassifyService;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentStructureNodeTypeEnum;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/
@Slf4j
@AllArgsConstructor
@Service
public class DocumentProfileServiceImpl implements DocumentProfileService {

    private static final int PROFILE_STATUS_SUCCESS = 2;

    private final DochubDocumentMapper documentMapper;
    private final DochubDocumentProfileMapper documentProfileMapper;
    private final DochubDocumentStructureNodeMapper structureNodeMapper;
    private final DocumentStorageService storageService;
    private final KnowledgeScopeClassifyService knowledgeScopeClassifyService;
    private final UidGenerator uidGenerator;

    @Override
    public DochubDocumentProfile generateProfile(Long documentId,
                                                     DocumentAnalysisResult analysisResult,
                                                     List<DochubDocumentStructureNode> structureNodes) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        DochubDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        String parsedText = analysisResult == null ? "" : StrUtil.blankToDefault(analysisResult.getParsedText(), "");
        List<DochubDocumentStructureNode> safeNodes = structureNodes == null ? List.of() : structureNodes;
        DocumentProfileDraft draft = buildDraft(document, parsedText, safeNodes);

        DochubDocumentProfile profile = documentProfileMapper.selectOne(new LambdaQueryWrapper<DochubDocumentProfile>()
            .eq(DochubDocumentProfile::getDocumentId, documentId)
            .eq(DochubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        boolean creating = profile == null;
        if (creating) {
            profile = new DochubDocumentProfile();
            profile.setId(uidGenerator.getUid());
            profile.setDocumentId(documentId);
            profile.setProfileVersion(1);
            profile.setStatus(BusinessStatus.YES.getCode());
        }
        else {
            profile.setProfileVersion(Optional.ofNullable(profile.getProfileVersion()).orElse(0) + 1);
        }
        profile.setDocumentSummary(draft.documentSummary());
        profile.setDocumentType(draft.documentType());
        profile.setCoreTopics(joinJsonLikeArray(draft.coreTopics()));
        profile.setExampleQuestions(joinJsonLikeArray(draft.exampleQuestions()));
        profile.setGraphFriendly(draft.graphFriendly() ? 1 : 0);
        profile.setSupportsGraphOutline(draft.supportsGraphOutline() ? 1 : 0);
        profile.setSupportsItemLookup(draft.supportsItemLookup() ? 1 : 0);
        profile.setSupportsGraphAssist(draft.supportsGraphAssist() ? 1 : 0);
        profile.setProfileSource("auto");
        profile.setProfileStatus(PROFILE_STATUS_SUCCESS);
        profile.setErrorMsg(null);
        if (creating) {
            documentProfileMapper.insert(profile);
        }
        else {
            documentProfileMapper.updateById(profile);
        }

        backfillDocumentMetadata(document, draft);
        log.info("文档画像生成完成: documentId={}, documentType={}, graphFriendly={}, supportsItemLookup={}, scopeCode='{}', businessCategory='{}', tags='{}'",
            documentId,
            draft.documentType(),
            draft.graphFriendly(),
            draft.supportsItemLookup(),
            draft.knowledgeScopeCode(),
            draft.businessCategory(),
            draft.documentTags());
        return profile;
    }

    @Override
    public Optional<DochubDocumentProfile> getByDocumentId(Long documentId) {
        if (documentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(documentProfileMapper.selectOne(new LambdaQueryWrapper<DochubDocumentProfile>()
            .eq(DochubDocumentProfile::getDocumentId, documentId)
            .eq(DochubDocumentProfile::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1")));
    }

    @Override
    public DochubDocumentProfile regenerateProfile(Long documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        DochubDocument document = documentMapper.selectById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        String parsedText = StrUtil.isBlank(document.getParseTextPath()) ? "" : storageService.downloadText(document.getParseTextPath());
        List<DochubDocumentStructureNode> structureNodes = structureNodeMapper.selectList(new LambdaQueryWrapper<DochubDocumentStructureNode>()
            .eq(DochubDocumentStructureNode::getDocumentId, documentId)
            .eq(DochubDocumentStructureNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(DochubDocumentStructureNode::getNodeNo, DochubDocumentStructureNode::getId));
        DocumentAnalysisResult analysisResult = new DocumentAnalysisResult();
        analysisResult.setParsedText(parsedText);
        return generateProfile(documentId, analysisResult, structureNodes);
    }

    @Override
    public List<DochubDocumentProfile> batchRegenerateProfiles(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        List<DochubDocumentProfile> profiles = new ArrayList<>();
        for (Long documentId : documentIds) {
            if (documentId == null) {
                continue;
            }
            profiles.add(regenerateProfile(documentId));
        }
        return profiles;
    }

    private DocumentProfileDraft buildDraft(DochubDocument document,
                                            String parsedText,
                                            List<DochubDocumentStructureNode> structureNodes) {
        List<String> sectionTitles = extractSectionTitles(structureNodes);
        boolean supportsItemLookup = structureNodes.stream().anyMatch(node -> node != null
            && (DocumentStructureNodeTypeEnum.STEP.getCode().equals(node.getNodeType())
            || DocumentStructureNodeTypeEnum.LIST_ITEM.getCode().equals(node.getNodeType())));
        boolean supportsGraphOutline = sectionTitles.size() >= 2;
        boolean graphFriendly = supportsItemLookup || supportsGraphOutline;
        String documentType = inferDocumentType(document, parsedText, sectionTitles, supportsItemLookup);
        List<String> coreTopics = buildCoreTopics(document, sectionTitles);
        List<String> exampleQuestions = buildExampleQuestions(documentType, coreTopics);
        String summary = buildSummary(document, sectionTitles, parsedText);

        // 优先用 LLM 对「文档内容 + 配置知识域」做归类（无合适知识域时自动生成新知识域/主题并持久化）；
        // LLM 不可用或失败时回退到关键词规则。
        KnowledgeClassifyResult classifyResult = knowledgeScopeClassifyService.classify(
            document.getDocumentName(), sectionTitles, parsedText);
        String knowledgeScopeCode;
        String knowledgeScopeName;
        String businessCategory;
        String llmTopic = "";
        if (classifyResult != null && StrUtil.isNotBlank(classifyResult.scopeCode())) {
            knowledgeScopeCode = classifyResult.scopeCode();
            knowledgeScopeName = StrUtil.blankToDefault(classifyResult.scopeName(), inferKnowledgeScopeName(knowledgeScopeCode));
            businessCategory = StrUtil.isNotBlank(classifyResult.businessCategory())
                ? classifyResult.businessCategory()
                : inferBusinessCategory(documentType, parsedText);
            llmTopic = StrUtil.blankToDefault(classifyResult.topicName(), "");
        }
        else {
            knowledgeScopeCode = inferKnowledgeScopeCode(document, sectionTitles, parsedText);
            knowledgeScopeName = inferKnowledgeScopeName(knowledgeScopeCode);
            businessCategory = inferBusinessCategory(documentType, parsedText);
        }
        String documentTags = buildDocumentTags(document, knowledgeScopeCode, documentType, coreTopics, llmTopic);
        return new DocumentProfileDraft(
            summary,
            documentType,
            coreTopics,
            exampleQuestions,
            graphFriendly,
            supportsGraphOutline,
            supportsItemLookup,
            true,
            knowledgeScopeCode,
            knowledgeScopeName,
            businessCategory,
            documentTags
        );
    }

    private void backfillDocumentMetadata(DochubDocument document, DocumentProfileDraft draft) {
        boolean changed = false;
        if (StrUtil.isBlank(document.getKnowledgeScopeCode()) && StrUtil.isNotBlank(draft.knowledgeScopeCode())) {
            document.setKnowledgeScopeCode(draft.knowledgeScopeCode());
            changed = true;
        }
        if (StrUtil.isBlank(document.getKnowledgeScopeName()) && StrUtil.isNotBlank(draft.knowledgeScopeName())) {
            document.setKnowledgeScopeName(draft.knowledgeScopeName());
            changed = true;
        }
        if (StrUtil.isBlank(document.getBusinessCategory()) && StrUtil.isNotBlank(draft.businessCategory())) {
            document.setBusinessCategory(draft.businessCategory());
            changed = true;
        }
        if (StrUtil.isBlank(document.getDocumentTags()) && StrUtil.isNotBlank(draft.documentTags())) {
            document.setDocumentTags(draft.documentTags());
            changed = true;
        }
        if (changed) {
            documentMapper.updateById(document);
        }
    }

    private List<String> extractSectionTitles(List<DochubDocumentStructureNode> structureNodes) {
        if (CollUtil.isEmpty(structureNodes)) {
            return List.of();
        }
        return structureNodes.stream()
            .filter(node -> node != null && DocumentStructureNodeTypeEnum.SECTION.getCode().equals(node.getNodeType()))
            .map(DochubDocumentStructureNode::getTitle)
            .filter(StrUtil::isNotBlank)
            .map(String::trim)
            .distinct()
            .limit(8)
            .toList();
    }

    private String inferDocumentType(DochubDocument document,
                                     String parsedText,
                                     List<String> sectionTitles,
                                     boolean supportsItemLookup) {
        String combined = combinedText(document, parsedText, sectionTitles);
        if (combined.contains("faq") || combined.contains("常见问题")) {
            return "faq";
        }
        if (combined.contains("故障") || combined.contains("排查") || combined.contains("检查顺序")) {
            return "troubleshooting";
        }
        if (combined.contains("规则") || combined.contains("制度")) {
            return "rule";
        }
        if (combined.contains("规格") || combined.contains("参数")) {
            return "spec";
        }
        if (supportsItemLookup || combined.contains("手册") || combined.contains("指南") || combined.contains("部署")) {
            return "manual";
        }
        return "intro";
    }

    private List<String> buildCoreTopics(DochubDocument document, List<String> sectionTitles) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        sectionTitles.stream().limit(6).forEach(title -> addTopic(topics, stripSectionCode(title)));
        addTopic(topics, stripFileExtension(document.getDocumentName()));
        return new ArrayList<>(topics).stream().filter(StrUtil::isNotBlank).limit(6).toList();
    }

    private void addTopic(Set<String> topics, String topic) {
        String normalized = StrUtil.blankToDefault(topic, "").trim();
        if (normalized.isBlank()) {
            return;
        }
        topics.add(normalized);
    }

    private List<String> buildExampleQuestions(String documentType, List<String> coreTopics) {
        List<String> examples = new ArrayList<>();
        for (String topic : coreTopics) {
            if ("troubleshooting".equals(documentType)) {
                examples.add(topic + "的可能原因有哪些？");
            }
            else if ("manual".equals(documentType)) {
                examples.add(topic + "的步骤是什么？");
            }
            else if ("rule".equals(documentType)) {
                examples.add(topic + "有哪些规则？");
            }
            else {
                examples.add(topic + "是什么意思？");
            }
        }
        return examples.stream().distinct().limit(6).toList();
    }

    private String buildSummary(DochubDocument document, List<String> sectionTitles, String parsedText) {
        StringBuilder builder = new StringBuilder();
        builder.append("文档《").append(StrUtil.blankToDefault(document.getDocumentName(), "未命名文档")).append("》");
        if (!sectionTitles.isEmpty()) {
            builder.append("主要涵盖：").append(String.join("、", sectionTitles.stream().limit(4).toList())).append("。");
        }
        String excerpt = StrUtil.blankToDefault(parsedText, "").replaceAll("\\s+", " ").trim();
        if (excerpt.length() > 180) {
            excerpt = excerpt.substring(0, 180);
        }
        if (StrUtil.isNotBlank(excerpt)) {
            builder.append("摘要：").append(excerpt);
        }
        return builder.toString().trim();
    }

    private String inferKnowledgeScopeCode(DochubDocument document,
                                           List<String> sectionTitles,
                                           String parsedText) {
        String combined = combinedText(document, parsedText, sectionTitles);
        if (containsAny(combined, "上线观察", "值班规则", "观察时长", "运营")) {
            return "operation_rule";
        }
        if (containsAny(combined, "机器人", "知识召回", "意图识别", "策略设计")) {
            return "robot_strategy";
        }
        if (containsAny(combined, "安装", "部署", "默认密码", "访问地址")) {
            return "deployment";
        }
        if (containsAny(combined, "故障", "排查", "异常", "检查顺序")) {
            return "troubleshooting";
        }
        if (containsAny(combined, "产品简介", "核心特性", "技术规格", "产品概述")) {
            return "product";
        }
        return "general_document";
    }

    private String inferKnowledgeScopeName(String scopeCode) {
        return switch (StrUtil.blankToDefault(scopeCode, "")) {
            case "operation_rule" -> "运营规则";
            case "robot_strategy" -> "机器人策略";
            case "deployment" -> "安装部署";
            case "troubleshooting" -> "故障排查";
            case "product" -> "产品资料";
            default -> "通用文档";
        };
    }

    private String inferBusinessCategory(String documentType, String parsedText) {
        if ("troubleshooting".equals(documentType)) {
            return "故障排查";
        }
        if ("rule".equals(documentType)) {
            return "规则";
        }
        if ("spec".equals(documentType)) {
            return "规格说明";
        }
        if ("manual".equals(documentType)) {
            return containsAny(parsedText.toLowerCase(Locale.ROOT), "步骤", "操作", "部署")
                ? "操作手册"
                : "手册";
        }
        return "介绍";
    }

    private String buildDocumentTags(DochubDocument document,
                                     String knowledgeScopeCode,
                                     String documentType,
                                     List<String> coreTopics,
                                     String llmTopic) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (StrUtil.isNotBlank(document.getDocumentTags())) {
            tags.addAll(List.of(document.getDocumentTags().split(",")));
        }
        addTag(tags, knowledgeScopeCode);
        addTag(tags, documentType);
        addTag(tags, llmTopic);
        coreTopics.stream().limit(4).forEach(topic -> addTag(tags, topic));
        return tags.stream()
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .distinct()
            .limit(8)
            .collect(Collectors.joining(","));
    }

    private void addTag(Set<String> tags, String tag) {
        String normalized = StrUtil.blankToDefault(tag, "").trim();
        if (normalized.isBlank()) {
            return;
        }
        tags.add(normalized);
    }

    private boolean containsAny(String text, String... values) {
        String normalized = StrUtil.blankToDefault(text, "").toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (normalized.contains(StrUtil.blankToDefault(value, "").toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String combinedText(DochubDocument document,
                                String parsedText,
                                List<String> sectionTitles) {
        return (StrUtil.blankToDefault(document.getDocumentName(), "") + " "
            + StrUtil.blankToDefault(document.getOriginalFileName(), "") + " "
            + String.join(" ", sectionTitles) + " "
            + StrUtil.blankToDefault(parsedText, ""))
            .toLowerCase(Locale.ROOT);
    }

    private String stripSectionCode(String title) {
        String normalized = StrUtil.blankToDefault(title, "").trim();
        return normalized.replaceFirst("^(第[一二三四五六七八九十百0-9]+[章节条部分]\\s*)|(\\d+(?:\\.\\d+)+\\s*)", "").trim();
    }

    private String stripFileExtension(String fileName) {
        String normalized = StrUtil.blankToDefault(fileName, "").trim();
        int index = normalized.lastIndexOf('.');
        return index > 0 ? normalized.substring(0, index) : normalized;
    }

    private String joinJsonLikeArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        return values.stream()
            .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
            .collect(Collectors.joining(",", "[", "]"));
    }

    private record DocumentProfileDraft(
        String documentSummary,
        String documentType,
        List<String> coreTopics,
        List<String> exampleQuestions,
        boolean graphFriendly,
        boolean supportsGraphOutline,
        boolean supportsItemLookup,
        boolean supportsGraphAssist,
        String knowledgeScopeCode,
        String knowledgeScopeName,
        String businessCategory,
        String documentTags
    ) {
    }
}
