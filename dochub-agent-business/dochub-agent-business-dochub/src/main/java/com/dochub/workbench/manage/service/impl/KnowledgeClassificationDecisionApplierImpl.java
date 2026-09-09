package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.model.classify.*;
import com.dochub.workbench.manage.service.KnowledgeClassificationDecisionApplier;
import com.dochub.workbench.manage.support.KnowledgeRouteCanonicalizer;
import org.javaup.enums.BusinessStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeClassificationDecisionApplierImpl implements KnowledgeClassificationDecisionApplier {
    private final DochubKnowledgeScopeNodeMapper scopeMapper;
    private final DochubKnowledgeTopicNodeMapper topicMapper;
    private final KnowledgeRouteCanonicalizer canonicalizer;
    private final UidGenerator uidGenerator;

    public KnowledgeClassificationDecisionApplierImpl(DochubKnowledgeScopeNodeMapper scopeMapper,
                                                       DochubKnowledgeTopicNodeMapper topicMapper,
                                                       KnowledgeRouteCanonicalizer canonicalizer,
                                                       UidGenerator uidGenerator) {
        this.scopeMapper = scopeMapper; this.topicMapper = topicMapper; this.canonicalizer = canonicalizer; this.uidGenerator = uidGenerator;
    }

    @Override
    public AppliedRoute apply(KnowledgeClassificationResult result) {
        if (result == null || result.decision() == ClassificationDecision.REVIEW_REQUIRED) {
            throw new IllegalArgumentException("待确认分类不能直接应用");
        }
        if (result.decision() == ClassificationDecision.REUSE || result.decision() == ClassificationDecision.MANUAL) {
            return reuse(result);
        }
        return create(result);
    }

    private AppliedRoute reuse(KnowledgeClassificationResult result) {
        String code = result.selectedScopeCode();
        if (StrUtil.isBlank(code) && result.proposal() != null) code = result.proposal().scopeCode();
        DochubKnowledgeScopeNode scope = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
            .eq(DochubKnowledgeScopeNode::getScopeCode, code).eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
        if (scope == null) throw new IllegalArgumentException("选择的知识域不存在: " + code);
        // The scope match is authoritative. A proposed topic that does not exist under the scope
        // (the model can echo the scope code as the topic) must not fail ingest: fall back to
        // attaching the document to the scope without a topic, which the operator can refine later.
        String topicCode = result.proposal() == null ? "" : StrUtil.blankToDefault(result.proposal().topicCode(), "");
        DochubKnowledgeTopicNode topic = StrUtil.isBlank(topicCode) ? null : findTopic(scope.getScopeCode(), topicCode);
        return new AppliedRoute(scope.getScopeCode(), scope.getScopeName(), topic == null ? "" : topic.getTopicCode(), topic == null ? "" : topic.getTopicName());
    }

    private AppliedRoute create(KnowledgeClassificationResult result) {
        RouteProposal proposal = result.proposal();
        if (proposal == null || StrUtil.isBlank(proposal.scopeName())) throw new IllegalArgumentException("新知识域提案不完整");
        DochubKnowledgeScopeNode scope = result.selectedCandidate() == null ? ensureScope(proposal) : existingScope(result.selectedScopeCode());
        DochubKnowledgeTopicNode topic = StrUtil.isBlank(proposal.topicName()) ? null : ensureTopic(scope.getScopeCode(), proposal);
        return new AppliedRoute(scope.getScopeCode(), scope.getScopeName(), topic == null ? "" : topic.getTopicCode(), topic == null ? "" : topic.getTopicName());
    }

    private DochubKnowledgeScopeNode existingScope(String code) {
        DochubKnowledgeScopeNode scope = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
            .eq(DochubKnowledgeScopeNode::getScopeCode, code).eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
        if (scope == null) throw new IllegalArgumentException("选择的知识域不存在: " + code);
        return scope;
    }

    private DochubKnowledgeScopeNode ensureScope(RouteProposal proposal) {
        String key = canonicalizer.canonicalKey(StrUtil.blankToDefault(proposal.scopeName(), proposal.scopeCode()));
        if (!Integer.valueOf(1).equals(scopeMapper.acquireCanonicalLock(key))) throw new IllegalStateException("无法获取知识域创建锁");
        try {
            DochubKnowledgeScopeNode existing = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
                .eq(DochubKnowledgeScopeNode::getCanonicalKey, key).eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
            if (existing != null) return existing;
            DochubKnowledgeScopeNode node = new DochubKnowledgeScopeNode();
            node.setId(uidGenerator.getUid()); node.setScopeCode(code(proposal.scopeCode(), key)); node.setScopeName(proposal.scopeName());
            node.setCanonicalKey(key); node.setDescription(proposal.scopeDescription()); node.setSortOrder(nextScopeSort()); node.setStatus(BusinessStatus.YES.getCode());
            try { scopeMapper.insert(node); return node; }
            catch (DuplicateKeyException duplicate) {
                DochubKnowledgeScopeNode winner = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
                    .eq(DochubKnowledgeScopeNode::getCanonicalKey, key).eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
                if (winner != null) return winner;
                throw duplicate;
            }
        } finally { scopeMapper.releaseCanonicalLock(key); }
    }

    private DochubKnowledgeTopicNode ensureTopic(String scopeCode, RouteProposal proposal) {
        String key = canonicalizer.canonicalKey(StrUtil.blankToDefault(proposal.topicName(), proposal.topicCode()));
        if (!Integer.valueOf(1).equals(topicMapper.acquireCanonicalLock(scopeCode, key))) throw new IllegalStateException("无法获取主题创建锁");
        try {
            DochubKnowledgeTopicNode existing = topicMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
                .eq(DochubKnowledgeTopicNode::getScopeCode, scopeCode).eq(DochubKnowledgeTopicNode::getCanonicalKey, key)
                .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
            if (existing != null) return existing;
            DochubKnowledgeTopicNode node = new DochubKnowledgeTopicNode();
            node.setId(uidGenerator.getUid()); node.setScopeCode(scopeCode); node.setTopicCode(code(proposal.topicCode(), key));
            node.setTopicName(proposal.topicName()); node.setCanonicalKey(key); node.setDescription(proposal.topicDescription());
            node.setSortOrder(nextTopicSort()); node.setStatus(BusinessStatus.YES.getCode());
            try { topicMapper.insert(node); return node; }
            catch (DuplicateKeyException duplicate) {
                DochubKnowledgeTopicNode winner = topicMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
                    .eq(DochubKnowledgeTopicNode::getScopeCode, scopeCode).eq(DochubKnowledgeTopicNode::getCanonicalKey, key)
                    .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
                if (winner != null) return winner;
                throw duplicate;
            }
        } finally { topicMapper.releaseCanonicalLock(scopeCode, key); }
    }

    private DochubKnowledgeTopicNode findTopic(String scopeCode, String topicCode) {
        if (StrUtil.isBlank(topicCode)) return null;
        return topicMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
            .eq(DochubKnowledgeTopicNode::getScopeCode, scopeCode).eq(DochubKnowledgeTopicNode::getTopicCode, topicCode)
            .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()).last("LIMIT 1"));
    }
    private int nextScopeSort() { DochubKnowledgeScopeNode n = scopeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>().orderByDesc(DochubKnowledgeScopeNode::getSortOrder).last("LIMIT 1")); return n == null || n.getSortOrder() == null ? 1 : n.getSortOrder() + 1; }
    private int nextTopicSort() { DochubKnowledgeTopicNode n = topicMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>().orderByDesc(DochubKnowledgeTopicNode::getSortOrder).last("LIMIT 1")); return n == null || n.getSortOrder() == null ? 1 : n.getSortOrder() + 1; }
    private String code(String suggested, String key) { String value = StrUtil.blankToDefault(suggested, key).toLowerCase().replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", ""); return StrUtil.isBlank(value) ? "scope_" + Math.abs(key.hashCode()) : value; }
}
