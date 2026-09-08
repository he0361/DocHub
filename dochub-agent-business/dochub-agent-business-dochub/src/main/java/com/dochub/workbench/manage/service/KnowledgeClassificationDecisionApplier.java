package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.AppliedRoute;
import com.dochub.workbench.manage.model.classify.KnowledgeClassificationResult;

public interface KnowledgeClassificationDecisionApplier {
    AppliedRoute apply(KnowledgeClassificationResult result);
}
