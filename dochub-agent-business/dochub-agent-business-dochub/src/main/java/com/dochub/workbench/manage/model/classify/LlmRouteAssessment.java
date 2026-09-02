package com.dochub.workbench.manage.model.classify;

public record LlmRouteAssessment(boolean proposesNew, String selectedRouteCode, RouteProposal proposal,
                                 double confidence, String reason) {
    public static LlmRouteAssessment unavailable(RouteProposal fallback, String reason) {
        return new LlmRouteAssessment(true, null, fallback, 0, reason);
    }
}
