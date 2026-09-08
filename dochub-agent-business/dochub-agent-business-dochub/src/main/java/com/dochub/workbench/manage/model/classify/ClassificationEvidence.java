package com.dochub.workbench.manage.model.classify;

import java.util.List;

public record ClassificationEvidence(List<RouteCandidate> candidates, boolean semanticAvailable,
                                     LlmRouteAssessment assessment, NewRouteValidation validation) {
    public ClassificationEvidence {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
}
