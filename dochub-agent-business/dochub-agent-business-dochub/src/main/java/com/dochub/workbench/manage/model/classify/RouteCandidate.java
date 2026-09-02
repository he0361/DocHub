package com.dochub.workbench.manage.model.classify;

public record RouteCandidate(RouteDescriptor route, double deterministicScore, double semanticScore,
                             double combinedScore, String reason) {
    public String routeCode() { return route == null ? "" : route.routeCode(); }
    public String routeName() { return route == null ? "" : route.routeName(); }
}
