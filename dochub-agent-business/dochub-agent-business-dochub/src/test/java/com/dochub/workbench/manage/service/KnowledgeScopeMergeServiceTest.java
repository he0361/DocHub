package com.dochub.workbench.manage.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeMergeAudit;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;
import com.dochub.workbench.manage.dto.KnowledgeScopeMergeDto;
import com.dochub.workbench.manage.mapper.*;
import com.dochub.workbench.manage.service.impl.KnowledgeScopeMergeServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class KnowledgeScopeMergeServiceTest {

    @Test
    void mergeMovesDocumentsTopicsAndDisablesSourceAtomically() {
        DochubDocumentMapper documentMapper = mock(DochubDocumentMapper.class);
        DochubKnowledgeScopeNodeMapper scopeMapper = mock(DochubKnowledgeScopeNodeMapper.class);
        DochubKnowledgeTopicNodeMapper topicMapper = mock(DochubKnowledgeTopicNodeMapper.class);
        DochubTopicDocumentRelationMapper relationMapper = mock(DochubTopicDocumentRelationMapper.class);
        DochubKnowledgeScopeMergeAuditMapper auditMapper = mock(DochubKnowledgeScopeMergeAuditMapper.class);
        DochubKnowledgeClassificationReviewMapper reviewMapper = mock(DochubKnowledgeClassificationReviewMapper.class);
        UidGenerator uidGenerator = mock(UidGenerator.class);
        DochubKnowledgeScopeNode source = scope("duplicate", "重复域");
        DochubKnowledgeScopeNode target = scope("canonical", "规范域");
        DochubKnowledgeTopicNode sourceTopic = topic("duplicate-topic", "model", "duplicate");
        DochubKnowledgeTopicNode targetTopic = topic("canonical-topic", "model", "canonical");
        when(scopeMapper.selectActiveByCode("duplicate")).thenReturn(source);
        when(scopeMapper.selectActiveByCode("canonical")).thenReturn(target);
        when(topicMapper.selectActiveByScope("duplicate")).thenReturn(List.of(sourceTopic));
        when(topicMapper.selectActiveByCanonicalKey("canonical", "model")).thenReturn(targetTopic);
        when(documentMapper.countActiveByScopeCode("duplicate")).thenReturn(2);
        when(documentMapper.countActiveByScopeCode("canonical")).thenReturn(4, 6);
        when(relationMapper.countActiveByScopeCode("duplicate")).thenReturn(3);
        when(relationMapper.countActiveByScopeCode("canonical")).thenReturn(5, 8);
        when(uidGenerator.getUid()).thenReturn(99L);
        KnowledgeScopeMergeService service = new KnowledgeScopeMergeServiceImpl(
            documentMapper, scopeMapper, topicMapper, relationMapper, reviewMapper, auditMapper,
            uidGenerator, mock(ApplicationEventPublisher.class));
        KnowledgeScopeMergeDto dto = new KnowledgeScopeMergeDto();
        dto.setSourceScopeCode("duplicate");
        dto.setTargetScopeCode("canonical");

        var result = service.merge("admin", dto);

        assertThat(result.getDocumentCount()).isEqualTo(2);
        assertThat(result.getTopicCount()).isEqualTo(1);
        verify(documentMapper).replaceScopeCode("duplicate", "canonical", "规范域");
        verify(relationMapper).restoreCanonicalRelations("duplicate-topic", "canonical-topic");
        verify(relationMapper).moveActiveRelations("duplicate-topic", "canonical-topic");
        verify(relationMapper).deactivateByTopicCode("duplicate-topic");
        verify(reviewMapper).replaceSelectedTopicCode("duplicate-topic", "canonical-topic");
        verify(topicMapper).deactivateByTopicCode("duplicate-topic");
        verify(scopeMapper).deactivateByScopeCode("duplicate");
        ArgumentCaptor<DochubKnowledgeScopeMergeAudit> auditCaptor = ArgumentCaptor.forClass(DochubKnowledgeScopeMergeAudit.class);
        verify(auditMapper).insert(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getDetailJson())
            .contains("\"targetDocumentsBefore\":4")
            .contains("\"targetDocumentsAfter\":6")
            .contains("\"targetRelationsBefore\":5")
            .contains("\"targetRelationsAfter\":8");
    }

    @Test
    void mergeRejectsADescendantTargetToAvoidCreatingAParentCycle() {
        DochubDocumentMapper documentMapper = mock(DochubDocumentMapper.class);
        DochubKnowledgeScopeNodeMapper scopeMapper = mock(DochubKnowledgeScopeNodeMapper.class);
        DochubKnowledgeTopicNodeMapper topicMapper = mock(DochubKnowledgeTopicNodeMapper.class);
        DochubTopicDocumentRelationMapper relationMapper = mock(DochubTopicDocumentRelationMapper.class);
        DochubKnowledgeClassificationReviewMapper reviewMapper = mock(DochubKnowledgeClassificationReviewMapper.class);
        DochubKnowledgeScopeNode source = scope("parent", "父域");
        DochubKnowledgeScopeNode target = scope("child", "子域");
        target.setParentScopeCode("parent");
        when(scopeMapper.selectActiveByCode("parent")).thenReturn(source);
        when(scopeMapper.selectActiveByCode("child")).thenReturn(target);
        KnowledgeScopeMergeService service = new KnowledgeScopeMergeServiceImpl(
            documentMapper, scopeMapper, topicMapper, relationMapper, reviewMapper,
            mock(DochubKnowledgeScopeMergeAuditMapper.class), mock(UidGenerator.class),
            mock(ApplicationEventPublisher.class));
        KnowledgeScopeMergeDto dto = new KnowledgeScopeMergeDto();
        dto.setSourceScopeCode("parent");
        dto.setTargetScopeCode("child");

        assertThatThrownBy(() -> service.merge("admin", dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("子级");
        verify(scopeMapper, never()).deactivateByScopeCode(anyString());
        verifyNoInteractions(documentMapper, relationMapper);
    }

    private static DochubKnowledgeScopeNode scope(String code, String name) {
        DochubKnowledgeScopeNode value = new DochubKnowledgeScopeNode();
        value.setScopeCode(code);
        value.setScopeName(name);
        value.setStatus(1);
        return value;
    }

    private static DochubKnowledgeTopicNode topic(String code, String key, String scope) {
        DochubKnowledgeTopicNode value = new DochubKnowledgeTopicNode();
        value.setTopicCode(code);
        value.setTopicName(code);
        value.setCanonicalKey(key);
        value.setScopeCode(scope);
        value.setStatus(1);
        return value;
    }
}
