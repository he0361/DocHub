package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.*;
import com.dochub.workbench.manage.mapper.*;
import com.dochub.workbench.manage.model.classify.*;
import com.dochub.workbench.manage.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class KnowledgeClassificationWorkflowServiceImpl implements KnowledgeClassificationWorkflowService {
    private final DochubDocumentMapper documentMapper;
    private final DochubDocumentProfileMapper profileMapper;
    private final DochubKnowledgeClassificationReviewMapper reviewMapper;
    private final DochubTopicDocumentRelationMapper relationMapper;
    private final KnowledgeScopeClassifyService classifier;
    private final KnowledgeClassificationDecisionApplier applier;
    private final ObjectMapper objectMapper;
    private final UidGenerator uidGenerator;

    public KnowledgeClassificationWorkflowServiceImpl(DochubDocumentMapper documentMapper, DochubDocumentProfileMapper profileMapper,
                                                       DochubKnowledgeClassificationReviewMapper reviewMapper,
                                                       DochubTopicDocumentRelationMapper relationMapper,
                                                       KnowledgeScopeClassifyService classifier,
                                                       KnowledgeClassificationDecisionApplier applier,
                                                       ObjectMapper objectMapper, UidGenerator uidGenerator) {
        this.documentMapper = documentMapper; this.profileMapper = profileMapper; this.reviewMapper = reviewMapper;
        this.relationMapper = relationMapper; this.classifier = classifier; this.applier = applier;
        this.objectMapper = objectMapper; this.uidGenerator = uidGenerator;
    }

    @Override
    @Transactional
    public WorkflowResult classifyAndApply(long documentId, int profileVersion) {
        DochubDocument document = requireDocument(documentId);
        DochubDocumentProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<DochubDocumentProfile>()
            .eq(DochubDocumentProfile::getDocumentId, documentId).eq(DochubDocumentProfile::getProfileVersion, profileVersion)
            .eq(DochubDocumentProfile::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
        if (profile == null) throw new IllegalArgumentException("文档画像不存在: " + documentId + "/" + profileVersion);
        return classifyAndApply(documentId, profileVersion, new ClassificationMaterial(document.getDocumentName(),
            profile.getDocumentSummary(), projectIdentifiers(document), parseArray(profile.getCoreTopics()),
            profile.getDocumentSummary() + " " + profile.getCoreTopics()));
    }

    @Override
    @Transactional
    public WorkflowResult classifyAndApply(long documentId, int profileVersion, ClassificationMaterial material) {
        DochubDocument document = requireDocument(documentId);
        KnowledgeClassificationResult result = isManual(document)
            ? manualResult(document)
            : classifier.classify(material);
        DochubKnowledgeClassificationReview review = saveEvidence(documentId, profileVersion, result);
        if (result.decision() == ClassificationDecision.REVIEW_REQUIRED) {
            review.setReviewStatus("PENDING"); reviewMapper.updateById(review);
            document.setClassificationStatus(ClassificationStatus.PENDING_REVIEW.name());
            document.setClassificationReviewId(review.getId());
            documentMapper.updateById(document);
            return WorkflowResult.pending(review.getId());
        }

        AppliedRoute applied = applier.apply(result);
        replaceRelation(documentId, applied, result.confidence());
        document.setKnowledgeScopeCode(applied.scopeCode()); document.setKnowledgeScopeName(applied.scopeName());
        document.setClassificationStatus(ClassificationStatus.CONFIRMED.name()); document.setClassificationReviewId(review.getId());
        documentMapper.updateById(document);
        review.setReviewStatus("APPLIED"); review.setSelectedScopeCode(applied.scopeCode()); review.setSelectedTopicCode(applied.topicCode());
        reviewMapper.updateById(review);
        return WorkflowResult.confirmed(review.getId(), applied);
    }

    private DochubKnowledgeClassificationReview saveEvidence(long documentId, int profileVersion, KnowledgeClassificationResult result) {
        DochubKnowledgeClassificationReview review = reviewMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeClassificationReview>()
            .eq(DochubKnowledgeClassificationReview::getDocumentId, documentId)
            .eq(DochubKnowledgeClassificationReview::getProfileVersion, profileVersion)
            .eq(DochubKnowledgeClassificationReview::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
        boolean create = review == null;
        if (create) { review = new DochubKnowledgeClassificationReview(); review.setId(uidGenerator.getUid()); review.setDocumentId(documentId); review.setProfileVersion(profileVersion); review.setVersion(1); review.setStatus(BusinessStatus.YES.getCode()); }
        review.setReviewStatus(result.decision() == ClassificationDecision.REVIEW_REQUIRED ? "PENDING" : "DECIDED");
        review.setDecisionType(result.decision().name()); review.setProposedScopeJson(json(result.proposal()));
        review.setProposedTopicJson(json(result.proposal())); review.setCandidateJson(json(result.candidates()));
        review.setEvidenceJson(json(Map.of("confidence", result.confidence(), "semanticAvailable", result.semanticAvailable(), "reason", result.reason())));
        review.setReason(result.reason()); review.setTrustLlm(0);
        if (create) reviewMapper.insert(review); else reviewMapper.updateById(review);
        return review;
    }

    private void replaceRelation(long documentId, AppliedRoute route, double confidence) {
        relationMapper.deactivateByDocumentId(documentId);
        if (StrUtil.isBlank(route.topicCode())) return;
        DochubTopicDocumentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<DochubTopicDocumentRelation>()
            .eq(DochubTopicDocumentRelation::getTopicCode, route.topicCode()).eq(DochubTopicDocumentRelation::getDocumentId, documentId).last("LIMIT 1"));
        if (relation == null) { relation = new DochubTopicDocumentRelation(); relation.setId(uidGenerator.getUid()); relation.setTopicCode(route.topicCode()); relation.setDocumentId(documentId); }
        relation.setRelationScore(BigDecimal.valueOf(confidence)); relation.setRelationSource("classification"); relation.setReason("知识分类确认"); relation.setStatus(BusinessStatus.YES.getCode());
        if (relation.getCreateTime() == null) relationMapper.insert(relation); else relationMapper.updateById(relation);
    }

    private boolean isManual(DochubDocument document) { return ClassificationStatus.CONFIRMED.name().equals(document.getClassificationStatus()) && StrUtil.isNotBlank(document.getKnowledgeScopeCode()); }
    private KnowledgeClassificationResult manualResult(DochubDocument document) {
        RouteDescriptor descriptor = new RouteDescriptor(document.getKnowledgeScopeCode(), document.getKnowledgeScopeName(), List.of(), "", List.of(), List.of(), List.of(document.getDocumentName()));
        RouteCandidate candidate = new RouteCandidate(descriptor, 1, 1, 1, "用户上传时明确指定");
        return new KnowledgeClassificationResult(ClassificationDecision.MANUAL, 1, new RouteProposal(document.getKnowledgeScopeCode(), document.getKnowledgeScopeName(), "", "", "", "", document.getBusinessCategory()), candidate, List.of(candidate), false, "人工指定知识域");
    }
    private DochubDocument requireDocument(long id) { DochubDocument value = documentMapper.selectById(id); if (value == null) throw new IllegalArgumentException("文档不存在: " + id); return value; }
    private List<String> projectIdentifiers(DochubDocument d) { return List.of(StrUtil.blankToDefault(d.getDocumentName(), ""), StrUtil.blankToDefault(d.getOriginalFileName(), "")); }
    private List<String> parseArray(String value) { if (StrUtil.isBlank(value)) return List.of(); try { return Arrays.asList(objectMapper.readValue(value, String[].class)); } catch (Exception ignored) { return List.of(value); } }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException("无法持久化分类证据", exception); } }
}
