package com.dochub.workbench.manage.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubKnowledgeClassificationReview;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentProfileMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeClassificationReviewMapper;
import com.dochub.workbench.manage.mapper.DochubTopicDocumentRelationMapper;
import com.dochub.workbench.manage.model.classify.ClassificationDecision;
import com.dochub.workbench.manage.model.classify.ClassificationMaterial;
import com.dochub.workbench.manage.model.classify.ClassificationStatus;
import com.dochub.workbench.manage.model.classify.KnowledgeClassificationResult;
import com.dochub.workbench.manage.model.classify.RouteProposal;
import com.dochub.workbench.manage.model.classify.WorkflowResult;
import com.dochub.workbench.manage.service.impl.KnowledgeClassificationWorkflowServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeClassificationWorkflowServiceTest {

    @Test
    void reviewRequiredFinishesProfileWithoutCreatingRoutes() {
        DochubDocumentMapper documentMapper = mock(DochubDocumentMapper.class);
        DochubDocumentProfileMapper profileMapper = mock(DochubDocumentProfileMapper.class);
        DochubKnowledgeClassificationReviewMapper reviewMapper = mock(DochubKnowledgeClassificationReviewMapper.class);
        DochubTopicDocumentRelationMapper relationMapper = mock(DochubTopicDocumentRelationMapper.class);
        KnowledgeScopeClassifyService classifier = mock(KnowledgeScopeClassifyService.class);
        KnowledgeClassificationDecisionApplier applier = mock(KnowledgeClassificationDecisionApplier.class);
        UidGenerator uidGenerator = mock(UidGenerator.class);
        DochubDocument document = new DochubDocument();
        document.setId(7L);
        document.setDocumentName("DocHub 模型配置");
        when(documentMapper.selectById(7L)).thenReturn(document);
        when(uidGenerator.getUid()).thenReturn(101L);
        when(classifier.classify(any())).thenReturn(new KnowledgeClassificationResult(
            ClassificationDecision.REVIEW_REQUIRED, 0.67,
            new RouteProposal("dochub", "DocHub", "", "model", "模型配置", "", ""),
            null, List.of(), false, "证据不足"));
        KnowledgeClassificationWorkflowService workflow = new KnowledgeClassificationWorkflowServiceImpl(
            documentMapper, profileMapper, reviewMapper, relationMapper, classifier, applier,
            new ObjectMapper(), uidGenerator);

        WorkflowResult result = workflow.classifyAndApply(7L, 3,
            new ClassificationMaterial("DocHub 模型配置", "配置说明", List.of("DocHub"), List.of("模型"), "配置"));

        assertThat(result.status()).isEqualTo(ClassificationStatus.PENDING_REVIEW);
        assertThat(document.getClassificationStatus()).isEqualTo(ClassificationStatus.PENDING_REVIEW.name());
        assertThat(document.getClassificationReviewId()).isEqualTo(101L);
        verify(reviewMapper).insert(any(DochubKnowledgeClassificationReview.class));
        verify(applier, never()).apply(any());
    }
}
