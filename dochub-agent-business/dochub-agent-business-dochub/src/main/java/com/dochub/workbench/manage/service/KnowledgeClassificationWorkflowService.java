package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.ClassificationMaterial;
import com.dochub.workbench.manage.model.classify.WorkflowResult;

public interface KnowledgeClassificationWorkflowService {
    WorkflowResult classifyAndApply(long documentId, int profileVersion);
    WorkflowResult classifyAndApply(long documentId, int profileVersion, ClassificationMaterial material);
}
