package com.dochub.workbench.manage.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.model.classify.AppliedRoute;
import com.dochub.workbench.manage.model.classify.ClassificationDecision;
import com.dochub.workbench.manage.model.classify.KnowledgeClassificationResult;
import com.dochub.workbench.manage.model.classify.RouteProposal;
import com.dochub.workbench.manage.service.impl.KnowledgeClassificationDecisionApplierImpl;
import com.dochub.workbench.manage.support.KnowledgeRouteCanonicalizer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeClassificationDecisionApplierTest {

    @Test
    void duplicateCanonicalInsertRereadsWinningScope() {
        DochubKnowledgeScopeNodeMapper scopeMapper = mock(DochubKnowledgeScopeNodeMapper.class);
        DochubKnowledgeTopicNodeMapper topicMapper = mock(DochubKnowledgeTopicNodeMapper.class);
        UidGenerator uidGenerator = mock(UidGenerator.class);
        DochubKnowledgeScopeNode winner = new DochubKnowledgeScopeNode();
        winner.setScopeCode("dochub");
        winner.setScopeName("DocHub 项目");
        when(scopeMapper.acquireCanonicalLock("dochubagent")).thenReturn(1);
        when(scopeMapper.selectOne(any())).thenReturn(null, winner);
        when(uidGenerator.getUid()).thenReturn(88L);
        doThrow(new DuplicateKeyException("canonical_key")).when(scopeMapper)
            .insert(any(DochubKnowledgeScopeNode.class));
        KnowledgeClassificationDecisionApplier applier = new KnowledgeClassificationDecisionApplierImpl(
            scopeMapper, topicMapper, new KnowledgeRouteCanonicalizer(), uidGenerator);
        KnowledgeClassificationResult result = new KnowledgeClassificationResult(
            ClassificationDecision.CREATE, 0.96,
            new RouteProposal("dochub", "DocHub Agent", "", "", "", "", ""),
            null, List.of(), true, "双重验证通过");

        AppliedRoute applied = applier.apply(result);

        assertThat(applied.scopeCode()).isEqualTo("dochub");
    }
}
