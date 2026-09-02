package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.*;
import com.dochub.workbench.manage.dto.*;
import com.dochub.workbench.manage.mapper.*;
import com.dochub.workbench.manage.model.classify.*;
import com.dochub.workbench.manage.service.*;
import com.dochub.workbench.manage.vo.KnowledgeClassificationReviewVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.enums.BusinessStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class KnowledgeClassificationReviewServiceImpl implements KnowledgeClassificationReviewService {
    private final DochubKnowledgeClassificationReviewMapper reviewMapper;
    private final DochubKnowledgeScopeNodeMapper scopeMapper;
    private final DochubKnowledgeTopicNodeMapper topicMapper;
    private final DochubDocumentMapper documentMapper;
    private final DochubTopicDocumentRelationMapper relationMapper;
    private final KnowledgeClassificationDecisionApplier applier;
    private final ObjectMapper objectMapper;
    private final UidGenerator uidGenerator;

    public KnowledgeClassificationReviewServiceImpl(DochubKnowledgeClassificationReviewMapper reviewMapper,
                                                     DochubKnowledgeScopeNodeMapper scopeMapper,
                                                     DochubKnowledgeTopicNodeMapper topicMapper,
                                                     DochubDocumentMapper documentMapper,
                                                     DochubTopicDocumentRelationMapper relationMapper,
                                                     KnowledgeClassificationDecisionApplier applier,
                                                     ObjectMapper objectMapper, UidGenerator uidGenerator) {
        this.reviewMapper = reviewMapper; this.scopeMapper = scopeMapper; this.topicMapper = topicMapper;
        this.documentMapper = documentMapper; this.relationMapper = relationMapper; this.applier = applier;
        this.objectMapper = objectMapper; this.uidGenerator = uidGenerator;
    }

    @Override
    public List<KnowledgeClassificationReviewVo> list(KnowledgeClassificationReviewQueryDto dto) {
        dto = dto == null ? new KnowledgeClassificationReviewQueryDto() : dto;
        LambdaQueryWrapper<DochubKnowledgeClassificationReview> query = new LambdaQueryWrapper<DochubKnowledgeClassificationReview>()
            .eq(DochubKnowledgeClassificationReview::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DochubKnowledgeClassificationReview::getCreateTime, DochubKnowledgeClassificationReview::getId);
        if (dto.getDocumentId() != null) query.eq(DochubKnowledgeClassificationReview::getDocumentId, dto.getDocumentId());
        if (StrUtil.isNotBlank(dto.getReviewStatus())) query.eq(DochubKnowledgeClassificationReview::getReviewStatus, dto.getReviewStatus());
        int pageNo = dto.getPageNo() == null || dto.getPageNo() < 1 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() < 1 ? 20 : Math.min(dto.getPageSize(), 100);
        query.last("LIMIT " + ((long) (pageNo - 1) * pageSize) + "," + pageSize);
        return reviewMapper.selectList(query).stream().map(this::toVo).toList();
    }

    @Override
    public KnowledgeClassificationReviewVo detail(KnowledgeClassificationReviewQueryDto dto) {
        if (dto == null || dto.getReviewId() == null) throw new IllegalArgumentException("reviewId 不能为空");
        return toVo(requireReview(dto.getReviewId()));
    }

    @Override
    @Transactional
    public KnowledgeClassificationReviewVo resolve(String operator, KnowledgeClassificationResolveDto dto) {
        DochubKnowledgeClassificationReview review = requireReview(dto.getReviewId());
        if (!"PENDING".equals(review.getReviewStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "该审核已处理");
        KnowledgeClassificationResult result;
        boolean trust = "TRUST_LLM_PROPOSAL".equals(dto.getMode());
        if (trust) {
            RouteProposal saved = readProposal(review.getProposedScopeJson());
            result = new KnowledgeClassificationResult(ClassificationDecision.CREATE, 1, saved, null, List.of(), false, "管理员相信已保存的 LLM 提案");
        } else if ("USE_EXISTING".equals(dto.getMode())) {
            DochubKnowledgeScopeNode scope = requireScope(dto.getScopeCode());
            DochubKnowledgeTopicNode topic = requireTopicOrNone(scope.getScopeCode(), dto.getTopicCode());
            RouteDescriptor descriptor = new RouteDescriptor(scope.getScopeCode(), scope.getScopeName(), List.of(), scope.getDescription(), List.of(), List.of(), List.of());
            RouteCandidate candidate = new RouteCandidate(descriptor, 1, 1, 1, "管理员选择");
            RouteProposal selection = new RouteProposal(scope.getScopeCode(), scope.getScopeName(), scope.getDescription(),
                topic == null ? "" : topic.getTopicCode(), topic == null ? "" : topic.getTopicName(), topic == null ? "" : topic.getDescription(), "");
            result = new KnowledgeClassificationResult(ClassificationDecision.REUSE, 1, selection, candidate, List.of(candidate), false, "管理员选择现有知识域");
        } else throw new IllegalArgumentException("mode 仅支持 USE_EXISTING 或 TRUST_LLM_PROPOSAL");

        String preliminaryScope = trust ? result.proposal().scopeCode() : result.selectedScopeCode();
        String preliminaryTopic = result.proposal() == null ? "" : result.proposal().topicCode();
        int claimed = reviewMapper.resolvePending(review.getId(), dto.getVersion(), preliminaryScope, preliminaryTopic, trust ? 1 : 0, operator);
        if (claimed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "审核版本已变化，请刷新后重试");

        AppliedRoute applied = applier.apply(result);
        DochubDocument document = documentMapper.selectById(review.getDocumentId());
        if (document == null) throw new IllegalArgumentException("审核关联文档不存在");
        replaceRelation(document.getId(), applied);
        document.setKnowledgeScopeCode(applied.scopeCode()); document.setKnowledgeScopeName(applied.scopeName());
        document.setClassificationStatus(ClassificationStatus.CONFIRMED.name()); document.setClassificationReviewId(review.getId());
        documentMapper.updateById(document);
        review.setReviewStatus("RESOLVED"); review.setSelectedScopeCode(applied.scopeCode()); review.setSelectedTopicCode(applied.topicCode());
        review.setTrustLlm(trust ? 1 : 0); review.setOperator(operator); review.setVersion(dto.getVersion() + 1);
        reviewMapper.updateById(review);
        return toVo(review);
    }

    private void replaceRelation(long documentId, AppliedRoute applied) {
        relationMapper.deactivateByDocumentId(documentId);
        if (StrUtil.isBlank(applied.topicCode())) return;
        DochubTopicDocumentRelation relation = relationMapper.selectOne(new LambdaQueryWrapper<DochubTopicDocumentRelation>()
            .eq(DochubTopicDocumentRelation::getDocumentId, documentId).eq(DochubTopicDocumentRelation::getTopicCode, applied.topicCode()).last("LIMIT 1"));
        if (relation == null) { relation = new DochubTopicDocumentRelation(); relation.setId(uidGenerator.getUid()); relation.setDocumentId(documentId); relation.setTopicCode(applied.topicCode()); }
        relation.setRelationScore(BigDecimal.ONE); relation.setRelationSource("review"); relation.setReason("管理员确认知识分类"); relation.setStatus(1);
        if (relation.getCreateTime() == null) relationMapper.insert(relation); else relationMapper.updateById(relation);
    }
    private DochubKnowledgeClassificationReview requireReview(Long id) { DochubKnowledgeClassificationReview value = reviewMapper.selectById(id); if (value == null || !Integer.valueOf(1).equals(value.getStatus())) throw new IllegalArgumentException("审核不存在"); return value; }
    private DochubKnowledgeScopeNode requireScope(String code) { if (StrUtil.isBlank(code)) throw new IllegalArgumentException("必须选择知识域"); DochubKnowledgeScopeNode value = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>().eq(DochubKnowledgeScopeNode::getScopeCode, code).eq(DochubKnowledgeScopeNode::getStatus, 1).last("LIMIT 1")); if (value == null) throw new IllegalArgumentException("知识域不存在"); return value; }
    private DochubKnowledgeTopicNode requireTopicOrNone(String scopeCode, String topicCode) { if (StrUtil.isBlank(topicCode)) return null; DochubKnowledgeTopicNode value = topicMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>().eq(DochubKnowledgeTopicNode::getTopicCode, topicCode).eq(DochubKnowledgeTopicNode::getScopeCode, scopeCode).eq(DochubKnowledgeTopicNode::getStatus, 1).last("LIMIT 1")); if (value == null) throw new IllegalArgumentException("主题不属于所选知识域"); return value; }
    private RouteProposal readProposal(String json) { try { RouteProposal proposal = objectMapper.readValue(json, RouteProposal.class); if (proposal == null || StrUtil.isBlank(proposal.scopeName())) throw new IllegalArgumentException("保存的 LLM 提案不完整"); return proposal; } catch (IllegalArgumentException e) { throw e; } catch (Exception e) { throw new IllegalArgumentException("保存的 LLM 提案不可解析", e); } }
    private KnowledgeClassificationReviewVo toVo(DochubKnowledgeClassificationReview row) { DochubDocument document = documentMapper.selectById(row.getDocumentId()); return new KnowledgeClassificationReviewVo(row.getId(), row.getDocumentId(), document == null ? "" : document.getDocumentName(), row.getProfileVersion(), row.getReviewStatus(), row.getDecisionType(), row.getProposedScopeJson(), row.getProposedTopicJson(), row.getCandidateJson(), row.getEvidenceJson(), row.getReason(), row.getSelectedScopeCode(), row.getSelectedTopicCode(), row.getTrustLlm(), row.getOperator(), row.getVersion(), row.getCreateTime(), row.getEditTime()); }
}
