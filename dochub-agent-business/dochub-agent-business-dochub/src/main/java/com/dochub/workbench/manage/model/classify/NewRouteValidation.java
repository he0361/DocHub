package com.dochub.workbench.manage.model.classify;

public record NewRouteValidation(boolean accepted, double confidence, String reason) {
    public static NewRouteValidation notRequired() { return new NewRouteValidation(false, 0, "not_required"); }
}
