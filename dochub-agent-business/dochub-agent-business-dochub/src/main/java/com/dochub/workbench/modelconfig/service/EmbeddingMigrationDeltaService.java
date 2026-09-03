package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.chatagent.data.DochubChatMemorySummary;
import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingMigrationDelta;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Idempotently replays durable active-runtime mutations against an inactive target. */
@Component
public class EmbeddingMigrationDeltaService {
    private static final int BATCH_SIZE = 200;
    private final DochubEmbeddingMigrationDeltaMapper deltaMapper;
    private final DochubEmbeddingModelMigrationMapper migrationMapper;
    private final DochubDocumentChunkMapper chunkMapper;
    private final DochubChatMemorySummaryMapper memoryMapper;
    private final QdrantVectorStore qdrant;

    public EmbeddingMigrationDeltaService(DochubEmbeddingMigrationDeltaMapper deltaMapper,
                                          DochubEmbeddingModelMigrationMapper migrationMapper,
                                          DochubDocumentChunkMapper chunkMapper,
                                          DochubChatMemorySummaryMapper memoryMapper,
                                          QdrantVectorStore qdrant) {
        this.deltaMapper = deltaMapper; this.migrationMapper = migrationMapper; this.chunkMapper = chunkMapper;
        this.memoryMapper = memoryMapper; this.qdrant = qdrant;
    }

    public void replayUntilDrained(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target) {
        while (true) {
            List<DochubEmbeddingMigrationDelta> batch = deltaMapper.nextPending(job.getId(), value(job.getLastDeltaSequence()), BATCH_SIZE);
            if (batch == null || batch.isEmpty()) return;
            Map<String, DochubEmbeddingMigrationDelta> latest = new LinkedHashMap<>();
            for (DochubEmbeddingMigrationDelta delta : batch) latest.put(delta.getResourceType() + ':' + delta.getResourceId(), delta);
            try {
                for (DochubEmbeddingMigrationDelta delta : latest.values()) replay(delta, job, target);
            } catch (RuntimeException exception) {
                for (DochubEmbeddingMigrationDelta delta : latest.values()) {
                    delta.setAttempts(value(delta.getAttempts()) + 1);
                    delta.setErrorSummary("增量回放失败，请检查源数据、向量服务和 Qdrant");
                    delta.setEditTime(new Date());
                    deltaMapper.updateById(delta);
                }
                throw exception;
            }
            long cursor = value(job.getLastDeltaSequence());
            for (DochubEmbeddingMigrationDelta delta : batch) {
                delta.setDeltaStatus("DONE"); delta.setAttempts(value(delta.getAttempts()) + 1); delta.setEditTime(new Date());
                deltaMapper.updateById(delta); cursor = Math.max(cursor, delta.getSequenceNo());
            }
            job.setLastDeltaSequence(cursor); job.setEditTime(new Date()); migrationMapper.updateById(job);
        }
    }

    private void replay(DochubEmbeddingMigrationDelta delta, DochubEmbeddingModelMigration job,
                        EmbeddingRuntimeSnapshot target) {
        if ("DOCUMENT_CHUNK".equals(delta.getResourceType())) {
            DochubDocumentChunk chunk = chunkMapper.selectById(delta.getResourceId());
            if (chunk == null || chunk.getStatus() == null || chunk.getStatus() != 1) {
                qdrant.deletePoints(job.getTargetDocumentCollection(), List.of(delta.getResourceId())); return;
            }
            if (chunk.getVectorStatus() == null || chunk.getVectorStatus() != 3) {
                throw new IllegalStateException("文档向量源数据尚未提交完成");
            }
            qdrant.upsert(job.getTargetDocumentCollection(), List.of(new QdrantVectorStore.Point(chunk.getId(),
                embed(target, chunk.getChunkText()), documentPayload(chunk, target.spec().modelName()))));
            return;
        }
        if ("DOCUMENT".equals(delta.getResourceType())) {
            qdrant.deleteByFilter(job.getTargetDocumentCollection(), Map.of("must", List.of(Map.of(
                "key", "document_id", "match", Map.of("value", delta.getResourceId())))));
            return;
        }
        if ("MEMORY".equals(delta.getResourceType())) {
            DochubChatMemorySummary summary = memoryMapper.selectById(delta.getResourceId());
            if ("DELETE".equals(delta.getOperation()) || summary == null || summary.getStatus() == null || summary.getStatus() != 1
                || summary.getSummaryText() == null || summary.getSummaryText().isBlank()) {
                qdrant.deletePoints(job.getTargetMemoryCollection(), List.of(delta.getResourceId())); return;
            }
            qdrant.upsert(job.getTargetMemoryCollection(), List.of(new QdrantVectorStore.Point(summary.getId(),
                embed(target, summary.getSummaryText()), Map.of("summary_id", summary.getId(),
                "conversation_id", summary.getConversationId(), "memory_text", summary.getSummaryText(),
                "embedding_model", target.spec().modelName()))));
        }
    }

    private float[] embed(EmbeddingRuntimeSnapshot target, String text) {
        List<float[]> vectors = target.model().embed(List.of(text));
        if (vectors == null || vectors.size() != 1 || vectors.get(0) == null || vectors.get(0).length != target.dimension()) {
            throw new IllegalStateException("增量向量维度不一致");
        }
        return vectors.get(0);
    }

    private Map<String, Object> documentPayload(DochubDocumentChunk chunk, String modelName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document_id", chunk.getDocumentId()); payload.put("task_id", chunk.getTaskId());
        payload.put("chunk_no", chunk.getChunkNo() == null ? 0 : chunk.getChunkNo());
        payload.put("chunk_text", chunk.getChunkText()); payload.put("section_path", chunk.getSectionPath() == null ? "" : chunk.getSectionPath());
        payload.put("canonical_path", chunk.getCanonicalPath() == null ? "" : chunk.getCanonicalPath());
        payload.put("embedding_model", modelName); return payload;
    }
    private long value(Long value) { return value == null ? 0L : value; }
    private int value(Integer value) { return value == null ? 0 : value; }
}
