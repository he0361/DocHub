package com.dochub.workbench.modelconfig.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dochub.workbench.chatagent.data.DochubChatMemorySummary;
import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.EmbeddingMigrationStatus;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.support.EmbeddingMigrationLease;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resumable keyset rebuild worker. It never mutates the active runtime before verified activation. */
@Service
public class EmbeddingCollectionRebuildWorker {
    private static final int PAGE_SIZE = 100;
    private static final int PROVIDER_BATCH_SIZE = 10;
    private final DochubEmbeddingModelMigrationMapper migrationMapper;
    private final DochubAiModelConfigMapper configMapper;
    private final DochubDocumentChunkMapper chunkMapper;
    private final DochubChatMemorySummaryMapper memoryMapper;
    private final QdrantVectorStore qdrant;
    private final OpenAiCompatibleModelFactory modelFactory;
    private final ModelCredentialCipher cipher;
    private final EmbeddingMigrationDeltaService deltaService;
    private final EmbeddingMigrationVerifier verifier;
    private final EmbeddingRuntimeActivator activator;
    private final EmbeddingMigrationLease lease;

    public EmbeddingCollectionRebuildWorker(DochubEmbeddingModelMigrationMapper migrationMapper,
                                            DochubAiModelConfigMapper configMapper,
                                            DochubDocumentChunkMapper chunkMapper,
                                            DochubChatMemorySummaryMapper memoryMapper,
                                            QdrantVectorStore qdrant,
                                            OpenAiCompatibleModelFactory modelFactory,
                                            ModelCredentialCipher cipher,
                                            EmbeddingMigrationDeltaService deltaService,
                                            EmbeddingMigrationVerifier verifier,
                                            EmbeddingRuntimeActivator activator,
                                            EmbeddingMigrationLease lease) {
        this.migrationMapper = migrationMapper; this.configMapper = configMapper; this.chunkMapper = chunkMapper;
        this.memoryMapper = memoryMapper; this.qdrant = qdrant; this.modelFactory = modelFactory;
        this.cipher = cipher; this.deltaService = deltaService; this.verifier = verifier;
        this.activator = activator; this.lease = lease;
    }

    private EmbeddingCollectionRebuildWorker(QdrantVectorStore qdrant) {
        this(null, null, null, null, qdrant, null, null, null, null, null, null);
    }

    public static EmbeddingCollectionRebuildWorker forVectorWrites(QdrantVectorStore qdrant) {
        return new EmbeddingCollectionRebuildWorker(qdrant);
    }

    public void resume(Long migrationId) {
        if (migrationId == null || lease == null || !lease.acquire(migrationId)) return;
        DochubEmbeddingModelMigration job = null;
        EmbeddingMigrationStatus phase = EmbeddingMigrationStatus.PENDING;
        try {
            job = migrationMapper.selectById(migrationId);
            if (job == null || terminal(job)) return;
            phase = status(job);
            DochubAiModelConfig config = targetConfig(job.getTargetConfigVersion());
            EmbeddingRuntimeSnapshot target = targetRuntime(job, config);
            if (phase == EmbeddingMigrationStatus.PENDING) {
                qdrant.ensureDocumentCollection(job.getTargetDocumentCollection(), job.getTargetDimension());
                qdrant.ensureCollection(job.getTargetMemoryCollection(), job.getTargetDimension());
                requireCollectionDimension(job.getTargetDocumentCollection(), job.getTargetDimension());
                requireCollectionDimension(job.getTargetMemoryCollection(), job.getTargetDimension());
                job.setDocumentTotal(chunkMapper.countMigrationSource());
                job.setMemoryTotal(memoryMapper.countMigrationSource());
                transition(job, EmbeddingMigrationStatus.REBUILDING_DOCUMENTS);
                phase = EmbeddingMigrationStatus.REBUILDING_DOCUMENTS;
            }
            if (phase == EmbeddingMigrationStatus.REBUILDING_DOCUMENTS) {
                rebuildDocuments(job, target);
                transition(job, EmbeddingMigrationStatus.REBUILDING_MEMORY);
                phase = EmbeddingMigrationStatus.REBUILDING_MEMORY;
            }
            if (phase == EmbeddingMigrationStatus.REBUILDING_MEMORY) {
                rebuildMemories(job, target);
                transition(job, EmbeddingMigrationStatus.CATCHING_UP);
                phase = EmbeddingMigrationStatus.CATCHING_UP;
            }
            if (phase == EmbeddingMigrationStatus.CATCHING_UP) {
                deltaService.replayUntilDrained(job, target);
                enterFinalizing(job, target);
                phase = EmbeddingMigrationStatus.FINALIZING;
            }
            if (phase == EmbeddingMigrationStatus.FINALIZING) {
                deltaService.replayUntilDrained(job, target);
                transition(job, EmbeddingMigrationStatus.VERIFYING);
                phase = EmbeddingMigrationStatus.VERIFYING;
            }
            if (phase == EmbeddingMigrationStatus.VERIFYING) {
                verifier.verify(job, target);
                transition(job, EmbeddingMigrationStatus.SWITCHING);
                phase = EmbeddingMigrationStatus.SWITCHING;
            }
            if (phase == EmbeddingMigrationStatus.SWITCHING) {
                verifier.verify(job, target);
                activator.activate(job, config, target, job.getOperator(), "ACTIVATE_EMBEDDING");
            }
        } catch (RuntimeException exception) {
            fail(job, phase, exception);
        } finally {
            lease.release(migrationId);
        }
    }

    public void rebuildDocuments(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target) {
        long cursor = value(job.getLastDocumentChunkId());
        while (true) {
            requireLease(job.getId());
            List<DochubDocumentChunk> rows = chunkMapper.selectMigrationBatch(cursor, PAGE_SIZE);
            if (rows == null || rows.isEmpty()) return;
            upsertDocumentBatch(job, target, rows);
            cursor = rows.get(rows.size() - 1).getId();
            job.setLastDocumentChunkId(cursor);
            job.setDocumentProcessed(value(job.getDocumentProcessed()) + rows.size());
            persist(job);
        }
    }

    public void rebuildMemories(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target) {
        long cursor = value(job.getLastMemorySummaryId());
        while (true) {
            requireLease(job.getId());
            List<DochubChatMemorySummary> rows = memoryMapper.selectMigrationBatch(cursor, PAGE_SIZE);
            if (rows == null || rows.isEmpty()) return;
            upsertMemoryBatch(job, target, rows);
            cursor = rows.get(rows.size() - 1).getId();
            job.setLastMemorySummaryId(cursor);
            job.setMemoryProcessed(value(job.getMemoryProcessed()) + rows.size());
            persist(job);
        }
    }

    public void upsertDocumentBatch(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target,
                                    List<DochubDocumentChunk> rows) {
        for (int start = 0; start < rows.size(); start += PROVIDER_BATCH_SIZE) {
            List<DochubDocumentChunk> batch = rows.subList(start, Math.min(rows.size(), start + PROVIDER_BATCH_SIZE));
            List<float[]> vectors = target.model().embed(batch.stream().map(DochubDocumentChunk::getChunkText).toList());
            requireVectorCount(batch.size(), vectors);
            List<QdrantVectorStore.Point> points = new ArrayList<>();
            for (int index = 0; index < batch.size(); index++) {
                DochubDocumentChunk chunk = batch.get(index);
                requireDimension(target.dimension(), vectors.get(index));
                points.add(new QdrantVectorStore.Point(chunk.getId(), vectors.get(index), documentPayload(chunk, target.spec().modelName())));
            }
            qdrant.upsert(job.getTargetDocumentCollection(), points);
        }
    }

    public void upsertMemoryBatch(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target,
                                  List<DochubChatMemorySummary> rows) {
        for (int start = 0; start < rows.size(); start += PROVIDER_BATCH_SIZE) {
            List<DochubChatMemorySummary> batch = rows.subList(start, Math.min(rows.size(), start + PROVIDER_BATCH_SIZE));
            List<float[]> vectors = target.model().embed(batch.stream().map(DochubChatMemorySummary::getSummaryText).toList());
            requireVectorCount(batch.size(), vectors);
            List<QdrantVectorStore.Point> points = new ArrayList<>();
            for (int index = 0; index < batch.size(); index++) {
                DochubChatMemorySummary summary = batch.get(index);
                requireDimension(target.dimension(), vectors.get(index));
                points.add(new QdrantVectorStore.Point(summary.getId(), vectors.get(index), Map.of(
                    "summary_id", summary.getId(), "conversation_id", summary.getConversationId(),
                    "memory_text", summary.getSummaryText(), "embedding_model", target.spec().modelName())));
            }
            qdrant.upsert(job.getTargetMemoryCollection(), points);
        }
    }

    private EmbeddingRuntimeSnapshot targetRuntime(DochubEmbeddingModelMigration job, DochubAiModelConfig config) {
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.EMBEDDING,
            CompatibilityPreset.valueOf(config.getCompatibilityPreset()), config.getBaseUrl(),
            "/v1/chat/completions", config.getRequestPath(), cipher.decrypt(config.getEncryptedApiKey()),
            config.getModelName(), null, null, config.getTimeoutMillis());
        EmbeddingModel model = modelFactory.embeddingModel(spec);
        return new EmbeddingRuntimeSnapshot(config.getConfigVersion(), model, spec, job.getTargetDimension(),
            job.getTargetDocumentCollection(), job.getTargetMemoryCollection());
    }

    private DochubAiModelConfig targetConfig(Long version) {
        DochubAiModelConfig config = configMapper.selectOne(new QueryWrapper<DochubAiModelConfig>()
            .eq("model_type", ModelType.EMBEDDING.name()).eq("config_version", version).eq("status", 1).last("LIMIT 1"));
        if (config == null) throw new DochubFrameException(404, "向量迁移目标配置不存在");
        return config;
    }

    private void transition(DochubEmbeddingModelMigration job, EmbeddingMigrationStatus next) {
        status(job).requireTransition(next);
        job.setMigrationStatus(next.name());
        persist(job);
    }

    private void persist(DochubEmbeddingModelMigration job) {
        job.setEditTime(new Date());
        migrationMapper.updateById(job);
    }

    private void fail(DochubEmbeddingModelMigration job, EmbeddingMigrationStatus phase, RuntimeException exception) {
        if (job == null || status(job) == EmbeddingMigrationStatus.COMPLETED) return;
        job.setResumeStatus((phase == EmbeddingMigrationStatus.SWITCHING ? EmbeddingMigrationStatus.VERIFYING : phase).name());
        job.setMigrationStatus(EmbeddingMigrationStatus.FAILED.name());
        job.setErrorSummary("迁移阶段 " + phase.name() + " 执行失败，请检查模型服务和 Qdrant 后重试");
        job.setFinishTime(new Date());
        persist(job);
    }

    private boolean terminal(DochubEmbeddingModelMigration job) {
        EmbeddingMigrationStatus status = status(job);
        return status == EmbeddingMigrationStatus.COMPLETED || status == EmbeddingMigrationStatus.FAILED;
    }

    private EmbeddingMigrationStatus status(DochubEmbeddingModelMigration job) {
        return EmbeddingMigrationStatus.valueOf(job.getMigrationStatus());
    }

    private Map<String, Object> documentPayload(DochubDocumentChunk chunk, String modelName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document_id", chunk.getDocumentId()); payload.put("task_id", chunk.getTaskId());
        payload.put("chunk_no", number(chunk.getChunkNo())); payload.put("source_type", number(chunk.getSourceType()));
        payload.put("section_path", text(chunk.getSectionPath())); payload.put("canonical_path", text(chunk.getCanonicalPath()));
        payload.put("chunk_text", text(chunk.getChunkText())); payload.put("embedding_model", modelName);
        return payload;
    }

    private void requireVectorCount(int expected, List<float[]> vectors) {
        if (vectors == null || vectors.size() != expected) throw new IllegalStateException("向量返回数量不一致");
    }
    private void requireDimension(int expected, float[] vector) {
        if (vector == null || vector.length != expected) throw new IllegalStateException("向量维度不一致");
        for (float value : vector) if (!Float.isFinite(value)) throw new IllegalStateException("向量包含非有限数值");
    }
    private long value(Long number) { return number == null ? 0L : number; }
    private int number(Integer number) { return number == null ? 0 : number; }
    private String text(String value) { return value == null ? "" : value; }

    private void requireLease(Long migrationId) {
        if (!lease.heartbeat(migrationId)) throw new IllegalStateException("向量迁移执行权已转移到其他实例");
    }

    private void requireCollectionDimension(String collection, int dimension) {
        if (qdrant.collectionDimension(collection) != dimension) {
            throw new IllegalStateException("目标集合维度不一致: " + collection);
        }
    }

    private void enterFinalizing(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target) {
        for (int attempt = 0; attempt < 3_000; attempt++) {
            requireLease(job.getId());
            deltaService.replayUntilDrained(job, target);
            if (migrationMapper.enterFinalizing(job.getId()) == 1) {
                job.setMigrationStatus(EmbeddingMigrationStatus.FINALIZING.name());
                return;
            }
            DochubEmbeddingModelMigration latest = migrationMapper.selectById(job.getId());
            if (latest != null && EmbeddingMigrationStatus.FINALIZING.name().equals(latest.getMigrationStatus())) {
                job.setMigrationStatus(EmbeddingMigrationStatus.FINALIZING.name());
                return;
            }
            try { Thread.sleep(100L); }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待向量写入完成时任务被中断", exception);
            }
        }
        throw new IllegalStateException("等待正在进行的向量写入完成超时");
    }
}
