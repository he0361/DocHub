package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeMergeAudit;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;
import com.dochub.workbench.manage.dto.KnowledgeScopeMergeDto;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeClassificationReviewMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeMergeAuditMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.mapper.DochubTopicDocumentRelationMapper;
import com.dochub.workbench.manage.service.KnowledgeScopeMergeService;
import com.dochub.workbench.manage.support.KnowledgeRouteChangedEvent;
import com.dochub.workbench.manage.vo.KnowledgeScopeMergeVo;
import org.javaup.enums.BusinessStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class KnowledgeScopeMergeServiceImpl implements KnowledgeScopeMergeService {
    private final DochubDocumentMapper documentMapper;
    private final DochubKnowledgeScopeNodeMapper scopeMapper;
    private final DochubKnowledgeTopicNodeMapper topicMapper;
    private final DochubTopicDocumentRelationMapper relationMapper;
    private final DochubKnowledgeClassificationReviewMapper reviewMapper;
    private final DochubKnowledgeScopeMergeAuditMapper auditMapper;
    private final UidGenerator uidGenerator;
    private final ApplicationEventPublisher eventPublisher;

    public KnowledgeScopeMergeServiceImpl(DochubDocumentMapper documentMapper,
                                          DochubKnowledgeScopeNodeMapper scopeMapper,
                                          DochubKnowledgeTopicNodeMapper topicMapper,
                                          DochubTopicDocumentRelationMapper relationMapper,
                                          DochubKnowledgeClassificationReviewMapper reviewMapper,
                                          DochubKnowledgeScopeMergeAuditMapper auditMapper,
                                          UidGenerator uidGenerator,
                                          ApplicationEventPublisher eventPublisher) {
        this.documentMapper = documentMapper;
        this.scopeMapper = scopeMapper;
        this.topicMapper = topicMapper;
        this.relationMapper = relationMapper;
        this.reviewMapper = reviewMapper;
        this.auditMapper = auditMapper;
        this.uidGenerator = uidGenerator;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public KnowledgeScopeMergeVo merge(String operator, KnowledgeScopeMergeDto dto) {
        if (dto == null || StrUtil.isBlank(dto.getSourceScopeCode()) || StrUtil.isBlank(dto.getTargetScopeCode())) {
            throw new IllegalArgumentException("源知识域和目标知识域不能为空");
        }
        String sourceCode = dto.getSourceScopeCode().trim();
        String targetCode = dto.getTargetScopeCode().trim();
        if (sourceCode.equals(targetCode)) {
            throw new IllegalArgumentException("源知识域与目标知识域不能相同");
        }
        DochubKnowledgeScopeNode source = requireScope(sourceCode);
        DochubKnowledgeScopeNode target = requireScope(targetCode);
        rejectDescendantTarget(sourceCode, target);
        List<DochubKnowledgeTopicNode> sourceTopics = topicMapper.selectActiveByScope(sourceCode);
        int documentCount = documentMapper.countActiveByScopeCode(sourceCode);
        int relationCount = relationMapper.countActiveByScopeCode(sourceCode);
        int targetDocumentsBefore = documentMapper.countActiveByScopeCode(targetCode);
        int targetRelationsBefore = relationMapper.countActiveByScopeCode(targetCode);

        for (DochubKnowledgeTopicNode sourceTopic : sourceTopics) {
            DochubKnowledgeTopicNode targetTopic = topicMapper.selectActiveByCanonicalKey(
                targetCode, sourceTopic.getCanonicalKey());
            if (targetTopic == null) {
                topicMapper.reassignScope(sourceTopic.getTopicCode(), targetCode);
                continue;
            }
            relationMapper.restoreCanonicalRelations(sourceTopic.getTopicCode(), targetTopic.getTopicCode());
            relationMapper.moveActiveRelations(sourceTopic.getTopicCode(), targetTopic.getTopicCode());
            relationMapper.deactivateByTopicCode(sourceTopic.getTopicCode());
            reviewMapper.replaceSelectedTopicCode(sourceTopic.getTopicCode(), targetTopic.getTopicCode());
            topicMapper.deactivateByTopicCode(sourceTopic.getTopicCode());
        }

        documentMapper.replaceScopeCode(sourceCode, targetCode, target.getScopeName());
        reviewMapper.replaceSelectedScopeCode(sourceCode, targetCode);
        scopeMapper.replaceParentScopeCode(sourceCode, targetCode);
        scopeMapper.deactivateByScopeCode(sourceCode);
        int targetDocumentsAfter = documentMapper.countActiveByScopeCode(targetCode);
        int targetRelationsAfter = relationMapper.countActiveByScopeCode(targetCode);

        DochubKnowledgeScopeMergeAudit audit = new DochubKnowledgeScopeMergeAudit();
        audit.setId(uidGenerator.getUid());
        audit.setSourceScopeCode(sourceCode);
        audit.setTargetScopeCode(targetCode);
        audit.setDocumentCount(documentCount);
        audit.setTopicCount(sourceTopics.size());
        audit.setRelationCount(relationCount);
        audit.setOperator(StrUtil.blankToDefault(operator, "unknown"));
        audit.setDetailJson("{\"sourceName\":\"" + jsonEscape(source.getScopeName())
            + "\",\"targetName\":\"" + jsonEscape(target.getScopeName())
            + "\",\"targetDocumentsBefore\":" + targetDocumentsBefore
            + ",\"targetDocumentsAfter\":" + targetDocumentsAfter
            + ",\"targetRelationsBefore\":" + targetRelationsBefore
            + ",\"targetRelationsAfter\":" + targetRelationsAfter + "}");
        audit.setStatus(BusinessStatus.YES.getCode());
        auditMapper.insert(audit);
        eventPublisher.publishEvent(new KnowledgeRouteChangedEvent("scope-merge:" + sourceCode + "->" + targetCode));
        return new KnowledgeScopeMergeVo(sourceCode, targetCode, documentCount, sourceTopics.size(), relationCount);
    }

    private DochubKnowledgeScopeNode requireScope(String code) {
        DochubKnowledgeScopeNode scope = scopeMapper.selectActiveByCode(code);
        if (scope == null) {
            throw new IllegalArgumentException("知识域不存在或已停用: " + code);
        }
        return scope;
    }

    private void rejectDescendantTarget(String sourceCode, DochubKnowledgeScopeNode target) {
        Set<String> visited = new HashSet<>();
        DochubKnowledgeScopeNode current = target;
        while (current != null && StrUtil.isNotBlank(current.getParentScopeCode())) {
            String parentCode = current.getParentScopeCode().trim();
            if (sourceCode.equals(parentCode)) {
                throw new IllegalArgumentException("目标知识域不能是源知识域的子级");
            }
            if (!visited.add(parentCode)) {
                throw new IllegalArgumentException("知识域层级存在循环，请先修复层级关系");
            }
            current = scopeMapper.selectActiveByCode(parentCode);
        }
    }

    private String jsonEscape(String value) {
        return StrUtil.blankToDefault(value, "").replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
