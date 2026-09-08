package com.dochub.workbench.manage.integration;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.config.KnowledgeClassificationProperties;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.model.classify.*;
import com.dochub.workbench.manage.service.KnowledgeClassificationPolicy;
import com.dochub.workbench.manage.service.KnowledgeRouteCandidateService;
import com.dochub.workbench.manage.service.impl.KnowledgeClassificationDecisionApplierImpl;
import com.dochub.workbench.manage.service.impl.KnowledgeRouteCandidateServiceImpl;
import com.dochub.workbench.manage.support.KnowledgeRouteCanonicalizer;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeClassificationRegressionTest {
    private final KnowledgeRouteCanonicalizer canonicalizer = new KnowledgeRouteCanonicalizer();
    private final KnowledgeRouteCandidateService candidates = new KnowledgeRouteCandidateServiceImpl(canonicalizer);
    private final KnowledgeClassificationPolicy policy = new KnowledgeClassificationPolicy(new KnowledgeClassificationProperties());

    @Test
    void twoDochubDocumentsResolveToOneKnowledgeScopeEvenWhenLlmProposesNew() throws IOException {
        RouteDescriptor dochub = new RouteDescriptor("dochub", "DocHub 项目", List.of("DocHub Agent"),
            "企业文档知识库、智能问答、知识路由与模型配置", List.of("如何配置 DocHub"),
            List.of("知识路由", "智能问答", "模型配置"), List.of("DocHub 知识路由治理", "DocHub 模型配置"));
        RouteDescriptor unrelated = new RouteDescriptor("finance", "财务", List.of(), "报销预算", List.of(), List.of("发票"), List.of());

        KnowledgeClassificationResult first = classifyFixture("dochub-project-document-a.txt", dochub, unrelated);
        KnowledgeClassificationResult second = classifyFixture("dochub-project-document-b.txt", dochub, unrelated);

        assertThat(first.decision()).isEqualTo(ClassificationDecision.REUSE);
        assertThat(second.decision()).isEqualTo(ClassificationDecision.REUSE);
        assertThat(first.selectedScopeCode()).isEqualTo(second.selectedScopeCode()).isEqualTo("dochub");
    }

    @Test
    void uncertainNewDomainRemainsPendingReview() throws IOException {
        String text = fixture("genuinely-new-domain-document.txt");
        RouteDescriptor dochub = new RouteDescriptor("dochub", "DocHub 项目", List.of("DocHub Agent"),
            "文档知识库与智能问答", List.of(), List.of("知识路由"), List.of());
        List<RouteCandidate> ranked = candidates.rankScopes(
            new ClassificationMaterial("卫星轨道力学", text, List.of("轨道实验"), List.of("轨道摄动"), text),
            List.of(dochub), 10);
        ClassificationEvidence evidence = new ClassificationEvidence(ranked, false,
            new LlmRouteAssessment(true, null, new RouteProposal("orbital", "轨道力学", "", "", "", "", ""), .78, "可能是新域"),
            new NewRouteValidation(true, .79, "仍需人工确认"));

        assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.REVIEW_REQUIRED);
    }

    @Test
    void concurrentTrueNewCreationProducesAtMostOneCanonicalScope() throws Exception {
        DochubKnowledgeScopeNodeMapper scopeMapper = mock(DochubKnowledgeScopeNodeMapper.class);
        DochubKnowledgeTopicNodeMapper topicMapper = mock(DochubKnowledgeTopicNodeMapper.class);
        UidGenerator uidGenerator = mock(UidGenerator.class);
        AtomicReference<DochubKnowledgeScopeNode> stored = new AtomicReference<>();
        AtomicInteger inserted = new AtomicInteger();
        AtomicInteger sequence = new AtomicInteger(100);
        CountDownLatch insertBarrier = new CountDownLatch(2);
        when(scopeMapper.acquireCanonicalLock("轨道力学")).thenReturn(1);
        when(scopeMapper.selectOne(any())).thenAnswer(call -> stored.get());
        when(uidGenerator.getUid()).thenAnswer(call -> (long) sequence.incrementAndGet());
        when(scopeMapper.insert(any(DochubKnowledgeScopeNode.class))).thenAnswer(call -> {
            DochubKnowledgeScopeNode candidate = call.getArgument(0);
            insertBarrier.countDown();
            insertBarrier.await(5, TimeUnit.SECONDS);
            if (!stored.compareAndSet(null, candidate)) {
                throw new DuplicateKeyException("canonical_key");
            }
            inserted.incrementAndGet();
            return 1;
        });
        KnowledgeClassificationDecisionApplierImpl applier = new KnowledgeClassificationDecisionApplierImpl(
            scopeMapper, topicMapper, canonicalizer, uidGenerator);
        KnowledgeClassificationResult create = new KnowledgeClassificationResult(ClassificationDecision.CREATE, .96,
            new RouteProposal("orbital", "轨道力学", "", "", "", "", ""), null, List.of(), false, "双重验证通过");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> applier.apply(create));
            var second = executor.submit(() -> applier.apply(create));
            assertThat(first.get(10, TimeUnit.SECONDS).scopeCode()).isEqualTo("orbital");
            assertThat(second.get(10, TimeUnit.SECONDS).scopeCode()).isEqualTo("orbital");
            assertThat(inserted).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void finalMigrationRefusesCollisionsBeforeAddingUniqueKeys() throws IOException {
        Path script = repositoryFile("sql", "dochub", "Mysql", "20260826_finalize_knowledge_canonical_unique.sql");
        String sql = Files.readString(script);
        assertThat(sql).contains("SIGNAL SQLSTATE '45000'")
            .contains("uk_scope_canonical_key")
            .contains("uk_topic_scope_canonical_key");
    }

    private KnowledgeClassificationResult classifyFixture(String name, RouteDescriptor... routes) throws IOException {
        String text = fixture(name);
        ClassificationMaterial material = new ClassificationMaterial(name, text, List.of("DocHub Agent"),
            List.of("知识路由", "智能问答", "模型配置"), text);
        List<RouteCandidate> ranked = candidates.rankScopes(material, List.of(routes), 10);
        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).combinedScore())
            .as("same-project candidate score: %s", ranked.get(0))
            .isGreaterThanOrEqualTo(.82);
        ClassificationEvidence evidence = new ClassificationEvidence(ranked, false,
            new LlmRouteAssessment(true, null, new RouteProposal("new_dochub", "DocHub 新域", "", "", "", "", ""), .97, "建议新建"),
            new NewRouteValidation(true, .95, "验证器误判新域"));
        return policy.decide(evidence);
    }

    private String fixture(String name) throws IOException {
        try (var stream = getClass().getResourceAsStream("/fixtures/classification/" + name)) {
            if (stream == null) throw new IOException("fixture not found: " + name);
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private Path repositoryFile(String... segments) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(Path.of(segments[0], java.util.Arrays.copyOfRange(segments, 1, segments.length)));
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IOException("repository file not found: " + String.join("/", segments));
    }
}
