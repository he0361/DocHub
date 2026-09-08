package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.ClassificationMaterial;
import com.dochub.workbench.manage.model.classify.RouteCandidate;
import com.dochub.workbench.manage.model.classify.RouteDescriptor;

import java.util.List;

public interface KnowledgeRouteCandidateService {
    List<RouteCandidate> rankScopes(ClassificationMaterial material, List<RouteDescriptor> routes, int limit);
}
