# Embedding Blue-Green Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a super administrator safely update embedding credentials/endpoints or replace the embedding model, while keeping the old model and vectors live until a verified background rebuild can switch atomically.

**Architecture:** All vector reads and writes capture one immutable `EmbeddingRuntimeSnapshot` containing the embedding model, model identity, document collection, and memory collection. Same-model endpoint/key changes test the candidate dimension and atomically replace only the model. A model-name change creates inactive versioned collections, rebuilds document chunks and conversation memories in the background, replays a durable mutation journal, enters a short finalization barrier, verifies counts and probes, then activates the new configuration and both collections as one snapshot. Failure leaves the old snapshot active and old collections retained.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring AI 1.1.0, MyBatis-Plus, MySQL 8, Redis, Qdrant REST API, Vue 3, Vite 6, JUnit 5, Mockito, Vitest.

## Global Constraints

- Every embedding endpoint requires `is_admin=1`; every save, migration, activation, retry, or rollback additionally requires the current administrator password and the exact phrase `我确认更改向量模型`.
- Password and confirmation phrase are request-only values: never persist, cache, audit verbatim, or log them.
- Base URL, request path, model name, and API key are configurable for remote or local OpenAI-compatible providers.
- A blank API key retains the current encrypted key; local deployment may explicitly clear it.
- Same model name plus URL/key change may hot-swap only after a successful embedding probe and exact dimension equality with the active collection.
- Any model-name change triggers a full rebuild even when dimensions match.
- The active embedding model and active Qdrant collection names are one atomic runtime snapshot.
- No failed probe, migration batch, catch-up, verification, Redis reload, or activation may disturb the old active snapshot.
- Document and conversation-memory collections are versioned and migrated together.
- Old model configuration and collections remain available for audited rollback; deletion is a separate future retention operation.
- Multiple application instances coordinate one migration through database state/leases and receive activation through Redis plus database version polling.

---

### Task 1: Make embedding model and collections an atomic runtime snapshot

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/EmbeddingRuntimeSnapshot.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/ModelRuntimeRegistry.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/DynamicEmbeddingModel.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DefaultDocumentVectorGateway.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentKnowledgeServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/ConversationVectorMemoryService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/KnowledgeRouteServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/runtime/EmbeddingRuntimeSnapshotTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/manage/service/DocumentVectorRuntimeConsistencyTest.java`

**Interfaces:**
- Produces: `ModelRuntimeRegistry.captureEmbedding(): EmbeddingRuntimeSnapshot`.
- Snapshot fields: config version, `EmbeddingModel`, runtime spec, dimension, document collection, memory collection.

- [ ] **Step 1: Write failing snapshot consistency tests**

```java
@Test void anOperationKeepsModelAndCollectionFromOneSnapshot() {
    registry.activateEmbedding(snapshot(1L, modelReturning(1536), "dochub_document_v1", "dochub_memory_v1"));
    vectorGateway.beforeUpsert(() -> registry.activateEmbedding(
        snapshot(2L, modelReturning(1024), "dochub_document_v2", "dochub_memory_v2")));
    vectorGateway.vectorize(List.of(chunk()));
    verify(qdrant).upsert(eq("dochub_document_v1"), argThat(points -> points.getFirst().vector().length == 1536));
}

@Test void retrievalNeverEmbedsWithOneVersionAndSearchesAnotherCollection() {
    knowledgeService.vectorSearch(request());
    verify(qdrant).search(eq(capturedSnapshot.documentCollection()), any(), anyInt(), any());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingRuntimeSnapshotTest,DocumentVectorRuntimeConsistencyTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because consumers currently obtain `EmbeddingModel` and fixed collection names independently.

- [ ] **Step 3: Capture once at each vector operation boundary**

```java
public record EmbeddingRuntimeSnapshot(
    long configVersion,
    EmbeddingModel model,
    ModelRuntimeSpec spec,
    int dimension,
    String documentCollection,
    String memoryCollection) {}

public void vectorize(List<DochubDocumentChunk> chunks) {
    EmbeddingRuntimeSnapshot runtime = modelRuntimeRegistry.captureEmbedding();
    List<float[]> embeddings = runtime.model().embed(texts(chunks));
    vectorStore.ensureDocumentCollection(runtime.documentCollection(), runtime.dimension());
    vectorStore.upsert(runtime.documentCollection(), points(chunks, embeddings, runtime.spec().modelName()));
}
```

Add explicit collection parameters to Qdrant helpers; stop calling fixed `documentCollection()`/`memoryCollection()` inside vector operations. Refactor document search, keyword fallback, memory save/search, and route semantic scoring to capture the runtime once per logical operation. `DynamicEmbeddingModel` remains the primary compatibility bean for callers that need only embeddings.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: write, delete, retrieval, memory, and mid-call activation tests pass without model/collection mixing.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/test
git commit -m "refactor: bind embedding model and vector collections"
```

### Task 2: Enforce second-factor confirmation and same-model hot swap

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/dto/EmbeddingModelChangeDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/vo/EmbeddingModelTestVo.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/security/EmbeddingChangeConfirmationGuard.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingModelChangeService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/EmbeddingModelChangeServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/controller/AdminModelConfigController.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth/service/AdminAuthService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth/service/impl/AdminAuthServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/security/EmbeddingChangeConfirmationGuardTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingModelChangeServiceTest.java`

**Interfaces:**
- Produces: `POST /admin/model-config/embedding/test` and `/embedding/change`.
- `change` returns either `ACTIVATED` for a same-model hot swap or `MIGRATION_STARTED` with migration ID for a model-name change.

- [ ] **Step 1: Write failing authorization, phrase, dimension, and activation tests**

```java
@Test void rejectsWrongPasswordBeforeTestingCandidate() {
    when(authService.verifyCurrentPassword("admin", "wrong")).thenReturn(false);
    assertThatThrownBy(() -> service.change("admin", dto("wrong", CONFIRMATION)))
        .hasMessageContaining("管理员密码错误");
    verifyNoInteractions(modelFactory);
}

@Test void rejectsPhraseThatIsNotExact() {
    assertThatThrownBy(() -> guard.verify("admin", "password", "我确认修改向量模型"))
        .hasMessageContaining("我确认更改向量模型");
}

@Test void sameModelAndDimensionActivatesWithoutRebuild() {
    ChangeResult result = service.change("admin", sameModelDto());
    assertThat(result.status()).isEqualTo("ACTIVATED");
    verify(migrationService, never()).start(any());
    verify(registry).activateEmbedding(argThat(snapshot -> snapshot.documentCollection().equals("dochub_document_active")));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingChangeConfirmationGuardTest,EmbeddingModelChangeServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because embedding change authorization and mode selection do not exist.

- [ ] **Step 3: Implement connection/dimension probes and hot-swap policy**

```java
public EmbeddingProbeResult testCandidate(ModelRuntimeSpec candidate) {
    EmbeddingModel model = factory.createEmbedding(candidate);
    float[] first = requireVector(model.embed("DocHub embedding connection probe"));
    float[] second = requireVector(model.embed("DocHub embedding stability probe"));
    if (first.length != second.length || first.length == 0) {
        throw new ModelConfigException("向量维度测试不稳定");
    }
    return new EmbeddingProbeResult(model, first.length, finite(first) && finite(second));
}
```

Read the current password hash through `AdminAuthService`; do not compare passwords inside model configuration code. Compare the trimmed model name exactly after provider normalization. For the same model name, require candidate dimension equal to the active snapshot and preserve both active collection names. Persist/activate the new config version only after the probe passes. For a different model name, save the config as `PENDING_REBUILD` and call the migration service without changing the active row or runtime.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: non-admin, password, phrase, blank-key retention, local-key clearing, endpoint failure, dimension mismatch, same-model activation, and model-name migration tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java dochub-agent-business/dochub-agent-business-dochub/src/test
git commit -m "feat: secure embedding model changes"
```

### Task 3: Persist and coordinate a resumable blue-green migration

**Files:**
- Create: `sql/dochub/Mysql/20260826_add_embedding_model_migration.sql`
- Modify: `sql/dochub/Mysql/create_table_dochub.sql`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/data/DochubEmbeddingModelMigration.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/data/DochubEmbeddingMigrationDelta.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/mapper/DochubEmbeddingModelMigrationMapper.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/mapper/DochubEmbeddingMigrationDeltaMapper.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/EmbeddingMigrationStatus.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingMigrationService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/EmbeddingMigrationServiceImpl.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/EmbeddingMigrationLease.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/EmbeddingMigrationRecoveryTask.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingMigrationServiceTest.java`

**Interfaces:**
- State machine: `PENDING -> REBUILDING_DOCUMENTS -> REBUILDING_MEMORY -> CATCHING_UP -> VERIFYING -> SWITCHING -> COMPLETED`; any pre-switch state may become `FAILED`.
- Produces: versioned collection names `${base}_v${configVersion}`.

- [ ] **Step 1: Write failing state-machine, exclusivity, and resume tests**

```java
@Test void onlyOneMigrationCanBeActive() {
    when(mapper.countActive()).thenReturn(1L);
    assertThatThrownBy(() -> service.start(candidate()))
        .hasMessageContaining("已有向量模型重建任务");
}

@Test void restartResumesFromPersistedCursorWithoutActivatingCandidate() {
    when(mapper.findRecoverable()).thenReturn(List.of(rebuildingAtChunk(9_000L)));
    recoveryTask.recover();
    verify(worker).resume(argThat(job -> job.getLastDocumentChunkId() == 9_000L));
    verify(registry, never()).activateEmbedding(any());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingMigrationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because migration persistence and coordination do not exist.

- [ ] **Step 3: Add state, progress, lease, and immutable source/target metadata**

Store source and target config versions, source/target model names and dimensions, four collection names, per-phase total/processed/failed counts, document and memory cursors, delta cursor, lease owner/expiry, error summary, start/switch/finish timestamps, operator, and optimistic version. Delta rows store migration ID, resource type (`DOCUMENT`/`MEMORY`), resource ID, operation (`UPSERT`/`DELETE`), sequence, status, attempts, and error.

Use a singleton `dochub_embedding_migration_lock` row plus a conditional lease update (`lease_expire_time < now OR lease_owner = ?`) and heartbeat. Recovery may resume building inactive collections; it must never infer success from collection existence alone. Prevent a second active migration by locking that row inside the start transaction and rechecking active states before insert.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: valid/invalid transitions, one-active-job, lease takeover after expiry, no takeover before expiry, cursor persistence, restart, and failure tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- sql/dochub/Mysql/20260826_add_embedding_model_migration.sql sql/dochub/Mysql/create_table_dochub.sql dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig
git commit -m "feat: persist embedding migration lifecycle"
```

### Task 4: Rebuild inactive document and memory collections with catch-up

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingCollectionRebuildWorker.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingMigrationDeltaService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/VectorMutationCoordinator.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/mapper/DochubDocumentChunkMapper.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/mapper/DochubChatMemorySummaryMapper.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DefaultDocumentVectorGateway.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/ConversationVectorMemoryService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/PersistentConversationMemoryService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentManageServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/service/impl/DocumentAsyncProcessServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingCollectionRebuildWorkerTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/support/VectorMutationCoordinatorTest.java`

**Interfaces:**
- Rebuilds: all current retrievable document chunks and all current conversation memory summaries.
- Journals: active vector mutations while a migration is running and replays them idempotently into target collections.

- [ ] **Step 1: Write failing batch, resume, delta, and deletion tests**

```java
@Test void rebuildUsesCandidateModelAndOnlyTargetCollection() {
    worker.rebuildDocuments(job(), candidateRuntime());
    verify(qdrant, atLeastOnce()).upsert(eq("dochub_document_v8"), anyList());
    verify(qdrant, never()).upsert(eq("dochub_document_v7"), anyList());
    verify(activeRegistry, never()).activateEmbedding(any());
}

@Test void writesDuringMigrationAreReplayedAfterBaseline() {
    mutationCoordinator.documentUpsert(activeRuntime(), newChunks());
    verify(qdrant).upsert("dochub_document_v7", activePoints);
    verify(deltaMapper).insert(argThat(delta -> delta.getResourceId().equals(DOCUMENT_ID)));
    deltaService.replay(job(), candidateRuntime());
    verify(qdrant).upsert("dochub_document_v8", candidatePoints);
}

@Test void deletionDeltaRemovesDocumentFromTarget() {
    deltaService.replay(deleteDocumentDelta(), candidateRuntime());
    verify(qdrant).deleteByFilter(eq("dochub_document_v8"), anyMap());
}

@Test void finalizationBarrierPreventsAStaleIndexTaskFromStarting() {
    when(migrationState.current()).thenReturn(finalizingMigration());
    assertThatThrownBy(() -> documentManageService.buildIndex(indexDto()))
        .hasMessageContaining("向量模型正在完成安全切换");
    verify(kafkaProducer, never()).sendIndexBuild(any());
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingCollectionRebuildWorkerTest,VectorMutationCoordinatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because rebuild and delta coordination do not exist.

- [ ] **Step 3: Implement keyset batches and deterministic points**

Use keyset pagination (`id > last_id ORDER BY id LIMIT batchSize`) rather than offset. Default to 100 texts per database page and provider sub-batches no larger than the configured embedding batch limit. Persist the cursor and counts only after Qdrant acknowledges `wait=true` upserts. Document point IDs remain chunk IDs. Change memory point IDs to stable summary row IDs and pass summary ID from `PersistentConversationMemoryService`, allowing idempotent rebuild and update.

Active writes continue against the old snapshot. When a migration is active, write a durable delta after the active mutation and replay from source-of-truth database text into the target; repeated deltas collapse by resource ID during replay. Before switch, enter `FINALIZING`: new mutators observe the barrier and retry briefly, both index-task creation and async execution reject/defer stale starts with a stable retryable business error, the worker drains deltas, performs a final ID/count reconciliation against current retrievable chunks and summaries, then proceeds to verification. Release the barrier on either switch or failure.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: batching, partial provider failure, resume, retry, upsert collapse, delete, memory rebuild, finalization barrier, and no-active-snapshot-mutation tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/test
git commit -m "feat: rebuild vector collections with catch-up"
```

### Task 5: Verify and atomically switch, with retained rollback

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingMigrationVerifier.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/EmbeddingRuntimeActivator.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/dto/EmbeddingRollbackDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/dto/EmbeddingMigrationRetryDto.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/EmbeddingMigrationServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/controller/AdminModelConfigController.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingMigrationVerifierTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingRuntimeActivatorTest.java`

**Interfaces:**
- Produces: `POST /admin/model-config/embedding/migration/status`, `/embedding/migration/retry`, and `/embedding/rollback`.
- Publishes: one embedding runtime version event after database activation commits.

- [ ] **Step 1: Write failing verification, atomic switch, and rollback tests**

```java
@Test void countMismatchLeavesOldRuntimeActive() {
    when(qdrant.count("dochub_document_v8")).thenReturn(999L);
    assertThatThrownBy(() -> verifier.verify(job())).hasMessageContaining("向量数量不一致");
    assertThat(registry.captureEmbedding().configVersion()).isEqualTo(7L);
}

@Test void successfulActivationSwapsModelAndBothCollectionsTogether() {
    activator.activate(job(), candidateRuntime());
    EmbeddingRuntimeSnapshot active = registry.captureEmbedding();
    assertThat(active.configVersion()).isEqualTo(8L);
    assertThat(active.documentCollection()).isEqualTo("dochub_document_v8");
    assertThat(active.memoryCollection()).isEqualTo("dochub_memory_v8");
}

@Test void rollbackReactivatesRetainedSnapshotAfterSecondFactor() {
    service.rollback("admin", rollbackDto(7L, PASSWORD, CONFIRMATION));
    assertThat(registry.captureEmbedding().configVersion()).isEqualTo(7L);
}

@Test void retryKeepsOldRuntimeAndResumesFailedMigration() {
    service.retry("admin", retryDto(FAILED_MIGRATION_ID, PASSWORD, CONFIRMATION));
    assertThat(registry.captureEmbedding().configVersion()).isEqualTo(7L);
    verify(worker).resume(argThat(job -> job.id().equals(FAILED_MIGRATION_ID)));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingMigrationVerifierTest,EmbeddingRuntimeActivatorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because verification and activation services do not exist.

- [ ] **Step 3: Implement verification and transaction/event ordering**

Verify Qdrant collection dimensions, expected active-source row counts, zero pending deltas, finite-vector sampling, same-text repeat stability, and search probes that return known seeded points from both document and memory collections. Under the finalization barrier, activate in this order:

1. Database transaction marks old config inactive, new config active, and migration `COMPLETED` with both collection names.
2. Current instance atomically sets the complete `EmbeddingRuntimeSnapshot`.
3. Publish Redis version event; peers rebuild the full snapshot from database and swap only after their own connection and collection probes pass.
4. Database version polling retries peers that miss the event.

Retry applies the second-factor guard, resets only retryable failed batches/cursors, and continues against the same inactive target collections without switching early. Rollback applies the same guard and full-snapshot probe, creates an audit entry, and activates a retained complete version. A peer that cannot load the new snapshot keeps its previous valid snapshot and raises an operational alert.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: count, dimension, pending delta, probe failure, transaction rollback, event loss, peer reload failure, successful switch, and rollback tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/manage/support/QdrantVectorStore.java dochub-agent-business/dochub-agent-business-dochub/src/test
git commit -m "feat: atomically activate rebuilt vector runtime"
```

### Task 6: Complete the embedding configuration and migration UI

**Files:**
- Modify: `dochub-web/src/views/admin/AdminModelConfigView.vue`
- Modify: `dochub-web/src/api/api.js`
- Create: `dochub-web/src/components/admin/EmbeddingModelChangeDialog.vue`
- Create: `dochub-web/src/components/admin/EmbeddingMigrationProgress.vue`
- Create: `dochub-web/src/components/admin/EmbeddingModelChangeDialog.test.js`

**Interfaces:**
- Consumes: embedding test/change/status/rollback APIs.
- Produces: second-factor dialog, safe-flow explanation, progress polling, failure details, active/target versions, and retained rollback action.

- [ ] **Step 1: Write failing confirmation and progress tests**

```javascript
it('requires both password and the exact Chinese confirmation phrase', async () => {
  render(EmbeddingModelChangeDialog, { props: { candidate, active } })
  await fireEvent.update(screen.getByLabelText('再次输入管理员密码'), 'secret')
  await fireEvent.update(screen.getByLabelText('确认文本'), '我确认修改向量模型')
  expect(screen.getByRole('button', { name: '开始安全更换' }).disabled).toBe(true)
  await fireEvent.update(screen.getByLabelText('确认文本'), '我确认更改向量模型')
  expect(screen.getByRole('button', { name: '开始安全更换' }).disabled).toBe(false)
})

it('explains that the old model remains active during rebuild', () => {
  render(EmbeddingMigrationProgress, { props: { migration: rebuilding } })
  expect(screen.getByText(/后台重建完成并校验通过后才会切换/)).toBeTruthy()
  expect(screen.getByText(/当前用户仍在使用旧向量模型/)).toBeTruthy()
})
```

- [ ] **Step 2: Run focused UI tests and verify RED**

Run: `npm --prefix dochub-web run test -- EmbeddingModelChangeDialog.test.js`

Expected: FAIL because the dialog and progress component do not exist.

- [ ] **Step 3: Implement the protected workflow and guidance**

The embedding card must show active model, masked API-key state, final request URL, active dimension, collection names, config version, and migration state. Require “测试连接” before enabling change. Open the second-factor dialog only after a successful test. Clear the password and confirmation phrase immediately after submit or close.

Use the exact global warning:

> 模型配置会影响所有用户和后台文档任务。请先测试连接再保存。Base URL 必须能从 DocHub 后端所在机器访问；本地模型不能填写仅对浏览器可见的地址。API Key 将加密保存且不会再次完整显示。更换向量模型会触发全量向量重建，重建期间请保持旧模型服务可用。

Show the five-step guide: choose local/remote and compatibility preset; enter Base URL/request path/model; test from the backend; save chat or confirm embedding change; monitor rebuild until automatic switch. Poll active migrations with backoff and stop on component unmount. Explain whether the result will hot-swap credentials or start a rebuild before final confirmation.

- [ ] **Step 4: Run UI tests and build**

Run: `npm --prefix dochub-web run test -- EmbeddingModelChangeDialog.test.js`

Run: `npm --prefix dochub-web run build`

Expected: tests pass and Vite exits 0.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-web/src
git commit -m "feat: add safe embedding migration controls"
```

### Task 7: Run failure-injection and end-to-end migration verification

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/integration/EmbeddingBlueGreenMigrationTest.java`
- Create: `scripts/verify-embedding-migration.ps1`
- Modify: `README.md`

**Interfaces:**
- Verifies: live reads continue on old runtime during rebuild, new writes are caught up, failure does not switch, success switches all fields, and rollback works.

- [ ] **Step 1: Add an integration test with controlled provider and Qdrant doubles**

```java
@Test void usersKeepOldRuntimeUntilSuccessfulAtomicSwitch() {
    MigrationId id = client.startEmbeddingChange(candidateModelB(), secondFactor());
    assertThat(client.activeEmbeddingVersion()).isEqualTo(7L);
    client.indexDocument(documentCreatedDuringMigration());
    provider.failBatchOnce(3);
    worker.resume(id);
    assertThat(client.search("new document")).usesVersion(7L);
    worker.complete(id);
    assertThat(client.search("new document")).usesVersion(8L).andCollection("dochub_document_v8");
}
```

- [ ] **Step 2: Run the focused integration test**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=EmbeddingBlueGreenMigrationTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: dimension mismatch, provider timeout, restart/resume, delta write/delete, verification failure, success, event loss, and rollback scenarios pass.

- [ ] **Step 3: Run a local smoke migration**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-embedding-migration.ps1 -BaseUrl http://127.0.0.1:8090`

Expected: script confirms old-version retrieval during rebuild, completed migration counts, active target collection, and searchable probe documents. The script must use environment variables for credentials and never echo them.

- [ ] **Step 4: Run complete verification**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am test`

Run: `npm --prefix dochub-web run test -- --run && npm --prefix dochub-web run build`

Expected: all backend/frontend tests and the production build pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/test scripts/verify-embedding-migration.ps1 README.md
git commit -m "test: verify embedding blue green migration"
```
