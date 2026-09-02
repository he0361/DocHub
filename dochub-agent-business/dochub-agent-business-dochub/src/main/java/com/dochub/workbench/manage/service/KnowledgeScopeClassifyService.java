package com.dochub.workbench.manage.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.config.KnowledgeClassificationProperties;
import com.dochub.workbench.manage.data.*;
import com.dochub.workbench.manage.mapper.*;
import com.dochub.workbench.manage.model.classify.*;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Side-effect-free classification. Route creation belongs exclusively to the decision applier. */
@Slf4j
@Service
public class KnowledgeScopeClassifyService {
    private final DochubKnowledgeScopeNodeMapper scopeMapper;
    private final DochubKnowledgeTopicNodeMapper topicMapper;
    private final DochubDocumentMapper documentMapper;
    private final KnowledgeRouteCandidateService candidateService;
    private final KnowledgeRouteSemanticCandidateService semanticService;
    private final KnowledgeRouteLlmReranker reranker;
    private final NewKnowledgeRouteValidator validator;
    private final KnowledgeClassificationPolicy policy;
    private final KnowledgeClassificationProperties properties;

    public KnowledgeScopeClassifyService(DochubKnowledgeScopeNodeMapper scopeMapper,
                                         DochubKnowledgeTopicNodeMapper topicMapper,
                                         DochubDocumentMapper documentMapper,
                                         KnowledgeRouteCandidateService candidateService,
                                         KnowledgeRouteSemanticCandidateService semanticService,
                                         KnowledgeRouteLlmReranker reranker,
                                         NewKnowledgeRouteValidator validator,
                                         KnowledgeClassificationPolicy policy,
                                         KnowledgeClassificationProperties properties) {
        this.scopeMapper = scopeMapper;
        this.topicMapper = topicMapper;
        this.documentMapper = documentMapper;
        this.candidateService = candidateService;
        this.semanticService = semanticService;
        this.reranker = reranker;
        this.validator = validator;
        this.policy = policy;
        this.properties = properties;
    }

    public KnowledgeClassificationResult classify(ClassificationMaterial material) {
        List<RouteDescriptor> routes = loadRoutes();
        List<RouteCandidate> lexical = candidateService.rankScopes(material, routes, properties.getLexicalLimit());
        SemanticCandidates semantic = semanticService.findScopes(material, routes, properties.getSemanticLimit());
        List<RouteCandidate> merged = merge(lexical, semantic).stream().limit(properties.getRerankLimit()).toList();
        LlmRouteAssessment assessment = reranker.assess(material, merged);
        NewRouteValidation validation = assessment.proposesNew()
            ? validator.validate(material, assessment.proposal(), merged)
            : NewRouteValidation.notRequired();
        return policy.decide(new ClassificationEvidence(merged, semantic.available(), assessment, validation));
    }

    private List<RouteDescriptor> loadRoutes() {
        List<DochubKnowledgeScopeNode> scopes = scopeMapper.selectList(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
            .eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(DochubKnowledgeScopeNode::getSortOrder, DochubKnowledgeScopeNode::getId));
        List<DochubKnowledgeTopicNode> topics = topicMapper.selectList(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
            .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()));
        Map<String, List<String>> topicNames = topics.stream().collect(Collectors.groupingBy(
            topic -> StrUtil.blankToDefault(topic.getScopeCode(), ""),
            Collectors.flatMapping(topic -> split(topic.getTopicName() + "," + StrUtil.blankToDefault(topic.getAliases(), "")).stream(), Collectors.toList())));

        List<DochubDocument> confirmed = documentMapper.selectList(new LambdaQueryWrapper<DochubDocument>()
            .eq(DochubDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(DochubDocument::getClassificationStatus, ClassificationStatus.CONFIRMED.name())
            .isNotNull(DochubDocument::getKnowledgeScopeCode));
        Map<String, List<String>> documents = confirmed.stream().collect(Collectors.groupingBy(
            document -> StrUtil.blankToDefault(document.getKnowledgeScopeCode(), ""),
            Collectors.mapping(document -> StrUtil.blankToDefault(document.getDocumentName(), "") + " " +
                StrUtil.blankToDefault(document.getDocumentTags(), ""), Collectors.toList())));

        return scopes.stream().map(scope -> new RouteDescriptor(scope.getScopeCode(), scope.getScopeName(), split(scope.getAliases()),
            scope.getDescription(), split(scope.getExamples()), topicNames.getOrDefault(scope.getScopeCode(), List.of()),
            documents.getOrDefault(scope.getScopeCode(), List.of()))).toList();
    }

    private List<RouteCandidate> merge(List<RouteCandidate> lexical, SemanticCandidates semantic) {
        Map<String, RouteCandidate> lexicalByCode = lexical.stream().collect(Collectors.toMap(RouteCandidate::routeCode,
            Function.identity(), (a, b) -> a, LinkedHashMap::new));
        Map<String, RouteCandidate> semanticByCode = semantic.candidates().stream().collect(Collectors.toMap(RouteCandidate::routeCode,
            Function.identity(), (a, b) -> a, LinkedHashMap::new));
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.addAll(lexicalByCode.keySet()); codes.addAll(semanticByCode.keySet());
        List<RouteCandidate> merged = new ArrayList<>();
        for (String code : codes) {
            RouteCandidate l = lexicalByCode.get(code), s = semanticByCode.get(code);
            RouteDescriptor route = l != null ? l.route() : s.route();
            double lexicalScore = l == null ? 0 : l.deterministicScore();
            double semanticScore = s == null ? 0 : s.semanticScore();
            double combined = semantic.available() ? Math.max(lexicalScore, lexicalScore * .65 + semanticScore * .35) : lexicalScore;
            merged.add(new RouteCandidate(route, lexicalScore, semanticScore, combined,
                (l == null ? "" : l.reason()) + (semantic.available() ? "；语义 " + String.format(Locale.ROOT, "%.2f", semanticScore) : "；语义不可用")));
        }
        merged.sort(Comparator.comparingDouble(RouteCandidate::combinedScore).reversed().thenComparing(RouteCandidate::routeCode));
        return merged;
    }

    private List<String> split(String value) {
        if (StrUtil.isBlank(value)) return List.of();
        return Arrays.stream(value.replace('[', ' ').replace(']', ' ').replace('"', ' ').split("[,，;；|\\n]"))
            .map(String::trim).filter(StrUtil::isNotBlank).distinct().toList();
    }
}
