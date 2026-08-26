# Knowledge Classification Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reuse existing knowledge scopes and topics reliably, auto-create only genuinely new high-confidence routes, and send uncertain decisions to an explicit administrator review flow before indexing.

**Architecture:** Classification becomes a side-effect-free pipeline: canonical normalization and deterministic scoring produce candidates, embeddings add semantic evidence, and the LLM only reranks a bounded candidate set. A separate conservative validator must agree before a new scope can be auto-created. The resulting `REUSE`, `CREATE`, or `REVIEW_REQUIRED` decision is persisted and then applied transactionally; pending review never marks parsing as failed, but a backend guard prevents indexing until it is resolved.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring AI 1.1.0, MyBatis-Plus, MySQL 8, Vue 3, Vite 6, JUnit 5, Mockito, Vitest.

## Global Constraints

- LLM output and LLM-reported confidence are evidence, never sole authority to create a scope or topic.
- Classification has no database write side effects; only the decision applier may mutate routes and relations.
- A document in `PENDING_REVIEW` completes parsing and strategy recommendation normally, but every index-build entry point must reject it.
- Existing-route candidates include normalized code/name, aliases, description, examples, topics, and document profile evidence.
- Automatic creation requires both threshold evidence and a second independent conservative validation; otherwise create a review item.
- Route creation is idempotent under concurrent document parsing by canonical unique keys and duplicate-key reread.
- Choosing “相信 LLM 创建” applies the saved proposal shown to the user; it does not run a different unreviewed classification.
- Topic/document relation updates and document scope metadata updates occur in one transaction.
- Thresholds are externalized and observable; do not bury numeric policy inside prompts.

---

### Task 1: Add classification state, review persistence, canonical keys, and pure scoring

**Files:**
- Create: `sql/dochub/Mysql/20260826_add_knowledge_classification_review.sql`
- Modify: `sql/dochub/Mysql/create_table_dochub.sql`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/data/DochubKnowledgeScopeNode.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/data/DochubKnowledgeTopicNode.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/data/DochubDocument.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/data/DochubKnowledgeClassificationReview.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/mapper/DochubKnowledgeClassificationReviewMapper.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/ClassificationDecision.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/ClassificationStatus.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/ClassificationMaterial.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/RouteCandidate.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/RouteDescriptor.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/RouteProposal.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/model/classify/KnowledgeClassificationResult.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/KnowledgeRouteCanonicalizer.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeRouteCandidateService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeRouteCandidateServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/support/KnowledgeRouteCanonicalizerTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeRouteCandidateServiceTest.java`

**Interfaces:**
- Produces: `KnowledgeRouteCanonicalizer.canonicalKey(String): String`.
- Produces: `KnowledgeRouteCandidateService.rankScopes(ClassificationMaterial, List<RouteDescriptor>, int): List<RouteCandidate>`.
- Stores: document `classification_status`, active `classification_review_id`, scope/topic `canonical_key`, and immutable review evidence JSON.

- [ ] **Step 1: Write failing canonicalization and candidate ranking tests**

```java
@ParameterizedTest
@CsvSource({
    "DocHub Agent,dochubagent",
    "DocHub-Agent,dochubagent",
    "ＤｏｃＨｕｂ　Agent,dochubagent"
})
void equivalentNamesHaveOneCanonicalKey(String value, String expected) {
    assertThat(canonicalizer.canonicalKey(value)).isEqualTo(expected);
}

@Test void sameProjectEvidenceRanksExistingScopeAheadOfNewProposal() {
    RouteCandidate existing = scope("dochub", "DocHub 项目", "文档知识库、智能问答、知识路由");
    List<RouteCandidate> ranked = service.rankScopes(
        material("DocHub 智能问答性能优化", "知识路由和模型配置"), List.of(existing), 5);
    assertThat(ranked).first().extracting(RouteCandidate::routeCode).isEqualTo("dochub");
    assertThat(ranked.getFirst().deterministicScore()).isGreaterThanOrEqualTo(0.72);
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeRouteCanonicalizerTest,KnowledgeRouteCandidateServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the new domain types and services do not exist.

- [ ] **Step 3: Add schema and deterministic scorer**

Add `canonical_key` to scope and topic tables, backfill non-colliding rows, report collisions before adding unique keys, and add `classification_status` plus `classification_review_id` to the document table. Backfill existing usable documents to `CONFIRMED`; only newly classified or explicitly failed documents begin in another state. Create `dochub_knowledge_classification_review` with document/profile versions, status, decision, proposed scope/topic JSON, candidate JSON, evidence JSON, reason, selected scope/topic, `trust_llm`, operator, optimistic `version`, and timestamps.

Until the historical collision report is empty and the final unique migration is applied, creation acquires a database canonical-key lock and rereads before insertion. New installations receive the unique scope key and `(scope_code, canonical_key)` topic key directly from `create_table_dochub.sql`; existing installations finalize those constraints in Task 6 after audited merges.

```java
public double deterministicScore(ClassificationMaterial material, RouteDescriptor route) {
    double identity = max(
        exactOrContainment(material.projectIdentifiers(), route.identifiers()),
        tokenSimilarity(material.titleAndSummary(), route.nameAndAliases()));
    double description = tokenSimilarity(material.semanticText(), route.descriptionAndExamples());
    double topic = tokenSimilarity(material.topics(), route.topicNamesAndAliases());
    return clamp(identity * 0.50 + description * 0.30 + topic * 0.20);
}
```

Normalize with Unicode NFKC, lowercase, punctuation/whitespace removal, stable Chinese text preservation, and explicit alias splitting. Do not collapse two existing rows silently during migration; emit a collision query for manual merge.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: canonicalization and ranking tests pass, including Chinese, full-width, case, punctuation, and alias cases.

- [ ] **Step 5: Commit**

```powershell
git add -- sql/dochub/Mysql/20260826_add_knowledge_classification_review.sql sql/dochub/Mysql/create_table_dochub.sql dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage
git commit -m "feat: add knowledge classification evidence model"
```

### Task 2: Replace create-on-LLM-output with layered classification

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeScopeClassifyService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/config/KnowledgeClassificationProperties.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeRouteSemanticCandidateService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeRouteLlmReranker.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/NewKnowledgeRouteValidator.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeClassificationPolicy.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/prompt/knowledge-scope-classify.st`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/prompt/knowledge-scope-new-validator.st`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeClassificationPolicyTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeScopeClassifyServiceTest.java`

**Interfaces:**
- Consumes: primary dynamic `EmbeddingModel` and `ChatModel` from the runtime configuration plan.
- Produces: side-effect-free `KnowledgeClassificationResult` with decision, confidence, proposal, candidates, and evidence.

- [ ] **Step 1: Write the decision-matrix tests before changing production behavior**

```java
@Test void strongExistingCandidateIsReusedEvenWhenLlmSaysNew() {
    Evidence evidence = evidence(0.88, 0.91, llmNew(0.96), validatorRejectsNew());
    assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.REUSE);
}

@Test void onlyTwoStageHighConfidenceNewRouteAutoCreates() {
    Evidence evidence = evidence(0.31, 0.28, llmNew(0.94), validatorAcceptsNew(0.93));
    assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.CREATE);
}

@Test void uncertainNewRouteRequiresReview() {
    Evidence evidence = evidence(0.58, 0.61, llmNew(0.78), validatorAcceptsNew(0.79));
    assertThat(policy.decide(evidence).decision()).isEqualTo(ClassificationDecision.REVIEW_REQUIRED);
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeClassificationPolicyTest,KnowledgeScopeClassifyServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the policy and side-effect-free classifier do not exist.

- [ ] **Step 3: Implement bounded candidates, semantic evidence, LLM reranking, and independent validation**

Use deterministic Top-10, semantic Top-10, merge by route identity, and send at most Top-8 to the reranker. If embedding fails, continue with deterministic evidence and record `semantic_available=false`; do not treat absence as proof that a route is new.

```java
public KnowledgeClassificationResult classify(ClassificationMaterial material) {
    List<RouteCandidate> lexical = candidateService.rankScopes(material, properties.getLexicalLimit());
    SemanticCandidates semantic = semanticService.findScopes(material, properties.getSemanticLimit());
    List<RouteCandidate> merged = merger.merge(lexical, semantic.candidates());
    LlmRouteAssessment assessment = reranker.assess(material, merged);
    NewRouteValidation validation = assessment.proposesNew()
        ? newRouteValidator.validate(material, assessment.proposal(), merged)
        : NewRouteValidation.notRequired();
    return policy.decide(new ClassificationEvidence(merged, semantic.available(), assessment, validation));
}
```

Configure separate scope and topic thresholds with initial conservative defaults: automatic reuse `0.82`, review band lower bound `0.55`, maximum existing similarity allowed for automatic new `0.42`, first LLM new confidence `0.92`, second validator confidence `0.90`, and minimum evidence margin `0.15`. Tests define boundary behavior; production metrics will guide later tuning.

The reranker prompt must state that multiple documents from one project normally share a scope, require comparison to every supplied candidate, and return strict JSON. The second prompt receives the best existing candidates and asks specifically for reasons the proposal is genuinely outside them. Remove `ensureScope` and `ensureTopic` from classification.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: all decision branches, fallback, malformed-JSON, and contradictory-LLM tests pass without database mutations.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/main/resources/prompt dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage
git commit -m "fix: make knowledge classification confidence governed"
```

### Task 3: Persist and transactionally apply classification decisions

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeClassificationWorkflowService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeClassificationDecisionApplier.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeClassificationWorkflowServiceImpl.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeClassificationDecisionApplierImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentProfileServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentAsyncProcessServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeClassificationWorkflowServiceTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeClassificationDecisionApplierTest.java`

**Interfaces:**
- Produces: `classifyAndApply(documentId, profileVersion)` returning `CONFIRMED`, `PENDING_REVIEW`, or `FAILED`.
- Persists: one current review per document/profile version and complete evidence for audit/replay.

- [ ] **Step 1: Write failing workflow and concurrency tests**

```java
@Test void reviewRequiredFinishesProfileButDoesNotCreateRoutes() {
    when(classifier.classify(any())).thenReturn(reviewRequired());
    WorkflowResult result = workflow.classifyAndApply(DOCUMENT_ID, 3);
    assertThat(result.status()).isEqualTo(ClassificationStatus.PENDING_REVIEW);
    verify(scopeMapper, never()).insert(any());
    verify(topicMapper, never()).insert(any());
    verify(reviewMapper).insert(argThat(row -> "PENDING".equals(row.getStatus())));
}

@Test void duplicateCanonicalInsertRereadsWinningScope() {
    doThrow(new DuplicateKeyException("canonical_key"))
        .when(scopeMapper).insert(any());
    when(scopeRepository.findByCanonicalKey("dochubagent")).thenReturn(existingScope());
    assertThat(applier.apply(autoCreate())).extracting(AppliedRoute::scopeCode).isEqualTo("dochub");
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeClassificationWorkflowServiceTest,KnowledgeClassificationDecisionApplierTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because workflow and applier classes do not exist.

- [ ] **Step 3: Implement the separation between deciding and applying**

```java
@Transactional
public WorkflowResult classifyAndApply(long documentId, int profileVersion) {
    KnowledgeClassificationResult result = classifier.classify(materialLoader.load(documentId));
    ReviewRecord evidence = reviewStore.saveEvidence(documentId, profileVersion, result);
    if (result.decision() == ClassificationDecision.REVIEW_REQUIRED) {
        documentStore.markClassificationPending(documentId, evidence.id());
        return WorkflowResult.pending(evidence.id());
    }
    AppliedRoute applied = decisionApplier.apply(result);
    relationService.replaceClassificationRelation(documentId, applied.topicCode());
    documentStore.confirmClassification(documentId, applied, evidence.id());
    reviewStore.markApplied(evidence.id(), applied);
    return WorkflowResult.confirmed(applied);
}
```

Preserve profile generation and strategy recommendation when the result is pending. On retry for the same document/profile version, update or reuse the existing current review rather than creating duplicate pending items. Explicit manual document scope values remain authoritative and are recorded as `MANUAL` evidence instead of being overwritten.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: reuse, auto-create, pending, retry, duplicate-key, manual override, and transaction rollback tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage
git commit -m "feat: apply knowledge classification decisions safely"
```

### Task 4: Add review resolution APIs and block indexing server-side

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/dto/KnowledgeClassificationReviewQueryDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/dto/KnowledgeClassificationResolveDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/vo/KnowledgeClassificationReviewVo.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/KnowledgeClassificationReviewService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeClassificationReviewServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/controller/KnowledgeManageController.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentManageServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentAsyncProcessServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeClassificationReviewServiceTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/DocumentIndexClassificationGuardTest.java`

**Interfaces:**
- Produces: `POST /manage/knowledge/classification/review/list`, `/detail`, `/resolve`.
- Resolve modes: `USE_EXISTING` with scope/topic codes, or `TRUST_LLM_PROPOSAL` with saved review version.

- [ ] **Step 1: Write failing resolution and index-guard tests**

```java
@Test void pendingReviewCannotQueueIndexBuild() {
    when(documentMapper.selectById(DOCUMENT_ID)).thenReturn(pendingDocument());
    assertThatThrownBy(() -> documentManageService.buildIndex(indexDto(DOCUMENT_ID)))
        .hasMessageContaining("知识域待确认");
    verify(kafkaProducer, never()).sendIndexBuild(any());
}

@Test void trustLlmAppliesExactlyTheSavedProposal() {
    service.resolve("admin", resolveDto(REVIEW_ID, 4, "TRUST_LLM_PROPOSAL"));
    verify(applier).apply(argThat(result -> result.proposal().equals(savedProposal)));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeClassificationReviewServiceTest,DocumentIndexClassificationGuardTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because review APIs and the index guard do not exist.

- [ ] **Step 3: Implement optimistic review resolution and defense-in-depth guards**

Allow an explicit “无主题” selection; otherwise validate that the selected topic belongs to the selected scope. Update pending review with `WHERE id=? AND version=? AND status='PENDING'`; return a conflict when another administrator has resolved it. For `TRUST_LLM_PROPOSAL`, read the proposal from the review row, apply it with the same canonical uniqueness rules, and record the trust flag and operator.

Require `classification_status=CONFIRMED` both before queuing Kafka and again inside the async index consumer so pending, failed, stale, or forged messages cannot bypass the gate. Return stable business error codes such as `KNOWLEDGE_CLASSIFICATION_PENDING` and `KNOWLEDGE_CLASSIFICATION_FAILED` for frontend handling.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: list/detail, both resolution modes, stale version, invalid topic/scope, already-resolved, and both index guards pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage
git commit -m "feat: add knowledge classification review workflow"
```

### Task 5: Add review and duplicate-scope merge controls to the admin UI

**Files:**
- Modify: `dochub-web/src/views/admin/AdminDocumentDetailView.vue`
- Modify: `dochub-web/src/views/admin/AdminKnowledgeRouteView.vue`
- Modify: `dochub-web/src/api/api.js`
- Create: `dochub-web/src/components/admin/KnowledgeClassificationReviewPanel.vue`
- Create: `dochub-web/src/components/admin/KnowledgeScopeMergeDialog.vue`
- Create: `dochub-web/src/components/admin/KnowledgeClassificationReviewPanel.test.js`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/dto/KnowledgeScopeMergeDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/data/DochubKnowledgeScopeMergeAudit.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/mapper/DochubKnowledgeScopeMergeAuditMapper.java`
- Modify: `sql/dochub/Mysql/20260826_add_knowledge_classification_review.sql`
- Modify: `sql/dochub/Mysql/create_table_dochub.sql`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeRouteServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/controller/KnowledgeManageController.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/KnowledgeScopeMergeServiceTest.java`

**Interfaces:**
- Consumes: review endpoints from Task 4.
- Produces: candidate picker grouped by scope/topic, saved-proposal preview, and explicit “相信 LLM 创建” action.
- Produces: audited `POST /manage/knowledge/scope/merge` for repairing historical duplicates.

- [ ] **Step 1: Write failing UI and merge transaction tests**

```javascript
it('requires an explicit choice before resolving a pending review', async () => {
  render(KnowledgeClassificationReviewPanel, { props: { review } })
  expect(screen.getByText(/系统未能高置信度确认知识域/)).toBeTruthy()
  expect(screen.getByRole('button', { name: '确认使用已有知识域' }).disabled).toBe(true)
  expect(screen.getByRole('button', { name: '相信 LLM 创建' })).toBeTruthy()
})
```

```java
@Test void mergeMovesDocumentsTopicsAndDisablesSourceAtomically() {
    service.merge("admin", mergeDto("duplicate", "canonical"));
    verify(documentMapper).replaceScopeCode("duplicate", "canonical");
    verify(relationMapper).moveToCanonicalScope("duplicate", "canonical");
    verify(mergeAuditMapper).insert(any());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `npm --prefix dochub-web run test -- KnowledgeClassificationReviewPanel.test.js`

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeScopeMergeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: both fail because the UI and merge API do not exist.

- [ ] **Step 3: Implement review guidance and audited merge**

Show the best existing candidates with score explanations, the proposed new scope/topic, and the reason for uncertainty. Display `PENDING_REVIEW` on the document page and disable the index button with an actionable explanation. Refresh document and route data after resolution.

The merge dialog must show affected document/topic counts and require a target scope. The backend transaction rewrites document/profile scope references, moves or deduplicates topic relations, preserves topic canonical uniqueness, disables the source scope, rebuilds the route index after commit, and writes before/after counts to audit.

- [ ] **Step 4: Run UI, backend, and build verification**

Run the commands from Step 2.

Expected: focused tests pass.

Run: `npm --prefix dochub-web run build`

Expected: Vite exits 0.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-web/src dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage sql/dochub/Mysql
git commit -m "feat: add knowledge review and scope merge controls"
```

### Task 6: Add an end-to-end regression fixture for same-project documents

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/integration/KnowledgeClassificationRegressionTest.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/resources/fixtures/classification/dochub-project-document-a.txt`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/resources/fixtures/classification/dochub-project-document-b.txt`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/resources/fixtures/classification/genuinely-new-domain-document.txt`
- Create: `sql/dochub/Mysql/20260826_finalize_knowledge_canonical_unique.sql`

**Interfaces:**
- Verifies: same-project inputs converge on one scope, ambiguous input remains pending, and true-new input creates at most one scope under concurrency.

- [ ] **Step 1: Write the regression test against the completed workflow**

```java
@Test void twoDochubDocumentsResolveToOneKnowledgeScope() {
    WorkflowResult first = fixture.classify("dochub-project-document-a.txt");
    WorkflowResult second = fixture.classify("dochub-project-document-b.txt");
    assertThat(first.scopeCode()).isEqualTo(second.scopeCode());
    assertThat(scopeRepository.countActiveByCanonicalKey(first.scopeCanonicalKey())).isOne();
}
```

- [ ] **Step 2: Run the regression test**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=KnowledgeClassificationRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: all convergence, review, and concurrent creation scenarios pass.

- [ ] **Step 3: Run the module and frontend suites**

Run the collision queries in `20260826_finalize_knowledge_canonical_unique.sql` on the migration fixture. Expected: the script refuses to add constraints while duplicate active canonical keys remain; after Task 5 merges them, it adds unique scope `canonical_key` and topic `(scope_code, canonical_key)` constraints successfully.

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am test`

Run: `npm --prefix dochub-web run test -- --run && npm --prefix dochub-web run build`

Expected: all tests and production build pass.

- [ ] **Step 4: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/test sql/dochub/Mysql/20260826_finalize_knowledge_canonical_unique.sql
git commit -m "test: cover knowledge classification convergence"
```
