package com.dochub.workbench.manage.model.classify;

import java.util.List;

public record KnowledgeClassificationResult(ClassificationDecision decision, double confidence,
                                            RouteProposal proposal, RouteCandidate selectedCandidate,
                                            List<RouteCandidate> candidates, boolean semanticAvailable,
                                            String reason) {
    public KnowledgeClassificationResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    public String selectedScopeCode() { return selectedCandidate == null ? "" : selectedCandidate.routeCode(); }
}
