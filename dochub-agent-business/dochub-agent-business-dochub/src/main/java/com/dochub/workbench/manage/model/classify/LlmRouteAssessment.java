package com.dochub.workbench.manage.model.classify;

public record LlmRouteAssessment(boolean proposesNew, String selectedRouteCode, RouteProposal proposal,
                                 double confidence, String reason, boolean proposesNewTopic) {
    public LlmRouteAssessment(boolean proposesNew, String selectedRouteCode, RouteProposal proposal,
                              double confidence, String reason) {
        this(proposesNew, selectedRouteCode, proposal, confidence, reason, false);
    }
    public static LlmRouteAssessment unavailable(RouteProposal fallback, String reason) {
        return new LlmRouteAssessment(true, null, fallback, 0, reason, false);
    }
}
