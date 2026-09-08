package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.config.KnowledgeClassificationProperties;
import com.dochub.workbench.manage.model.classify.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeClassificationPolicyTest {

    private final KnowledgeClassificationPolicy policy = new KnowledgeClassificationPolicy(new KnowledgeClassificationProperties());

    @Test
    void strongExistingCandidateIsReusedEvenWhenLlmSaysNew() {
        ClassificationEvidence evidence = evidence(0.88, 0.91,
            new LlmRouteAssessment(true, null, proposal(), 0.96, "建议新建"),
            new NewRouteValidation(false, 0.20, "与现有域重叠"));
        assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.REUSE);
    }

    @Test
    void onlyTwoStageHighConfidenceNewRouteAutoCreates() {
        ClassificationEvidence evidence = evidence(0.31, 0.28,
            new LlmRouteAssessment(true, null, proposal(), 0.94, "确属新领域"),
            new NewRouteValidation(true, 0.93, "独立验证通过"));
        assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.CREATE);
    }

    @Test
    void uncertainNewRouteRequiresReview() {
        ClassificationEvidence evidence = evidence(0.58, 0.61,
            new LlmRouteAssessment(true, null, proposal(), 0.78, "可能新建"),
            new NewRouteValidation(true, 0.79, "证据不足"));
        assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.REVIEW_REQUIRED);
    }

    private ClassificationEvidence evidence(double deterministic, double semantic,
                                              LlmRouteAssessment assessment, NewRouteValidation validation) {
        RouteDescriptor route = new RouteDescriptor("dochub", "DocHub", List.of(), "", List.of(), List.of(), List.of());
        return new ClassificationEvidence(List.of(new RouteCandidate(route, deterministic, semantic, Math.max(deterministic, semantic), "")),
            true, assessment, validation);
    }

    private RouteProposal proposal() {
        return new RouteProposal("new_scope", "新知识域", "", "new_topic", "新主题", "", "");
    }
}
