package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.ClassificationMaterial;
import com.dochub.workbench.manage.model.classify.RouteCandidate;
import com.dochub.workbench.manage.model.classify.RouteDescriptor;
import com.dochub.workbench.manage.service.impl.KnowledgeRouteCandidateServiceImpl;
import com.dochub.workbench.manage.support.KnowledgeRouteCanonicalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRouteCandidateServiceTest {

    private final KnowledgeRouteCandidateService service =
        new KnowledgeRouteCandidateServiceImpl(new KnowledgeRouteCanonicalizer());

    @Test
    void sameProjectEvidenceRanksExistingScopeAheadOfUnrelatedScope() {
        RouteDescriptor dochub = new RouteDescriptor("dochub", "DocHub 项目", List.of("DocHub Agent"),
            "文档知识库、智能问答、知识路由", List.of("模型配置"), List.of("知识路由", "智能问答"), List.of("DocHub 部署手册"));
        RouteDescriptor finance = new RouteDescriptor("finance", "财务", List.of(),
            "报销、发票、预算", List.of(), List.of("报销"), List.of());
        ClassificationMaterial material = new ClassificationMaterial("DocHub 智能问答性能优化", "知识路由和模型配置",
            List.of("DocHub"), List.of("知识路由", "模型配置"), "DocHub Agent 对话模型配置和知识路由优化");

        List<RouteCandidate> ranked = service.rankScopes(material, List.of(finance, dochub), 5);

        assertThat(ranked).first().extracting(RouteCandidate::routeCode).isEqualTo("dochub");
        assertThat(ranked.get(0).deterministicScore()).isGreaterThanOrEqualTo(0.72);
    }
}
