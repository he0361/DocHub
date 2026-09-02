package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.config.KnowledgeClassificationProperties;
import com.dochub.workbench.manage.model.classify.*;
import org.springframework.stereotype.Service;

import java.util.Comparator;

@Service
public class KnowledgeClassificationPolicy {
    private final KnowledgeClassificationProperties properties;

    public KnowledgeClassificationPolicy(KnowledgeClassificationProperties properties) {
        this.properties = properties;
    }

    public KnowledgeClassificationResult decide(ClassificationEvidence evidence) {
        RouteCandidate best = evidence.candidates().stream()
            .max(Comparator.comparingDouble(RouteCandidate::combinedScore)).orElse(null);
        RouteCandidate second = evidence.candidates().stream()
            .sorted(Comparator.comparingDouble(RouteCandidate::combinedScore).reversed()).skip(1).findFirst().orElse(null);
        double bestScore = best == null ? 0 : best.combinedScore();
        double margin = bestScore - (second == null ? 0 : second.combinedScore());
        var threshold = properties.getScope();
        LlmRouteAssessment assessment = evidence.assessment();

        // Deterministic/semantic evidence can veto a hallucinated "new" answer.
        if (best != null && bestScore >= threshold.getAutomaticReuse()
            && (margin >= threshold.getMinimumEvidenceMargin() || evidence.candidates().size() == 1)) {
            return result(ClassificationDecision.REUSE, bestScore, assessment, best, evidence,
                "现有知识域证据达到自动复用阈值");
        }
        if (assessment != null && !assessment.proposesNew() && best != null
            && assessment.selectedRouteCode() != null) {
            RouteCandidate selected = evidence.candidates().stream()
                .filter(candidate -> assessment.selectedRouteCode().equals(candidate.routeCode())).findFirst().orElse(best);
            if (selected.combinedScore() >= threshold.getReviewLowerBound()
                && assessment.confidence() >= threshold.getAutomaticReuse()) {
                return result(ClassificationDecision.REUSE, Math.min(selected.combinedScore(), assessment.confidence()), assessment,
                    selected, evidence, "候选证据与 LLM 复用结论一致");
            }
        }
        NewRouteValidation validation = evidence.validation();
        if (assessment != null && assessment.proposesNew()
            && assessment.proposal() != null
            && bestScore <= threshold.getMaximumExistingForAutomaticNew()
            && assessment.confidence() >= threshold.getLlmNewConfidence()
            && validation != null && validation.accepted()
            && validation.confidence() >= threshold.getValidatorConfidence()) {
            return result(ClassificationDecision.CREATE, Math.min(assessment.confidence(), validation.confidence()), assessment,
                null, evidence, "两阶段新知识域验证通过");
        }
        return result(ClassificationDecision.REVIEW_REQUIRED, Math.max(bestScore, assessment == null ? 0 : assessment.confidence()),
            assessment, best, evidence, "现有域复用或新建证据不足，需要管理员确认");
    }

    private KnowledgeClassificationResult result(ClassificationDecision decision, double confidence,
                                                  LlmRouteAssessment assessment, RouteCandidate selected,
                                                  ClassificationEvidence evidence, String reason) {
        return new KnowledgeClassificationResult(decision, confidence,
            assessment == null ? null : assessment.proposal(), selected, evidence.candidates(), evidence.semanticAvailable(), reason);
    }
}
