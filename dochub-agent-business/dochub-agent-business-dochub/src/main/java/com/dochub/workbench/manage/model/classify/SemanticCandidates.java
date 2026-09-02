package com.dochub.workbench.manage.model.classify;

import java.util.List;

public record SemanticCandidates(List<RouteCandidate> candidates, boolean available, String error) {
    public SemanticCandidates { candidates = candidates == null ? List.of() : List.copyOf(candidates); }
    public static SemanticCandidates unavailable(String error) { return new SemanticCandidates(List.of(), false, error); }
}
