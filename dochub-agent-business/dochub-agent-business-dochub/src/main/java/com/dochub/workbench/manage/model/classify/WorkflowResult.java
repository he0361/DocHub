package com.dochub.workbench.manage.model.classify;

public record WorkflowResult(ClassificationStatus status, Long reviewId, AppliedRoute appliedRoute) {
    public static WorkflowResult pending(Long id) { return new WorkflowResult(ClassificationStatus.PENDING_REVIEW, id, null); }
    public static WorkflowResult confirmed(Long id, AppliedRoute route) { return new WorkflowResult(ClassificationStatus.CONFIRMED, id, route); }
}
