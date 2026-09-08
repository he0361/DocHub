package com.dochub.workbench.manage.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubKnowledgeClassificationReview;
import com.dochub.workbench.manage.dto.KnowledgeClassificationResolveDto;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeClassificationReviewMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.mapper.DochubTopicDocumentRelationMapper;
import com.dochub.workbench.manage.model.classify.AppliedRoute;
import com.dochub.workbench.manage.model.classify.KnowledgeClassificationResult;
import com.dochub.workbench.manage.model.classify.RouteProposal;
import com.dochub.workbench.manage.service.impl.KnowledgeClassificationReviewServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeClassificationReviewServiceTest {

    @Test
    void trustLlmAppliesExactlyTheSavedProposal() throws Exception {
        DochubKnowledgeClassificationReviewMapper reviewMapper = mock(DochubKnowledgeClassificationReviewMapper.class);
        DochubKnowledgeScopeNodeMapper scopeMapper = mock(DochubKnowledgeScopeNodeMapper.class);
        DochubKnowledgeTopicNodeMapper topicMapper = mock(DochubKnowledgeTopicNodeMapper.class);
        DochubDocumentMapper documentMapper = mock(DochubDocumentMapper.class);
        DochubTopicDocumentRelationMapper relationMapper = mock(DochubTopicDocumentRelationMapper.class);
        KnowledgeClassificationDecisionApplier applier = mock(KnowledgeClassificationDecisionApplier.class);
        ObjectMapper objectMapper = new ObjectMapper();
        RouteProposal saved = new RouteProposal("saved", "保存的提案", "原始描述", "saved_topic", "保存主题", "主题描述", "业务");
        DochubKnowledgeClassificationReview review = new DochubKnowledgeClassificationReview();
        review.setId(5L);
        review.setDocumentId(7L);
        review.setReviewStatus("PENDING");
        review.setVersion(4);
        review.setStatus(1);
        review.setProposedScopeJson(objectMapper.writeValueAsString(saved));
        DochubDocument document = new DochubDocument();
        document.setId(7L);
        when(reviewMapper.selectById(5L)).thenReturn(review);
        when(reviewMapper.resolvePending(5L, 4, "saved", "saved_topic", 1, "admin")).thenReturn(1);
        when(documentMapper.selectById(7L)).thenReturn(document);
        when(applier.apply(any())).thenReturn(new AppliedRoute("saved", "保存的提案", "saved_topic", "保存主题"));
        KnowledgeClassificationReviewService service = new KnowledgeClassificationReviewServiceImpl(
            reviewMapper, scopeMapper, topicMapper, documentMapper, relationMapper, applier,
            objectMapper, mock(UidGenerator.class));
        KnowledgeClassificationResolveDto dto = new KnowledgeClassificationResolveDto();
        dto.setReviewId(5L);
        dto.setVersion(4);
        dto.setMode("TRUST_LLM_PROPOSAL");

        service.resolve("admin", dto);

        ArgumentCaptor<KnowledgeClassificationResult> result = ArgumentCaptor.forClass(KnowledgeClassificationResult.class);
        verify(applier).apply(result.capture());
        assertThat(result.getValue().proposal()).isEqualTo(saved);
    }
}
