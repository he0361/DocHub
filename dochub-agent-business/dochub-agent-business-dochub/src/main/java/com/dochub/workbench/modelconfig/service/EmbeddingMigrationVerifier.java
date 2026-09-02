package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Component;

/** Performs deterministic pre-switch checks against current source-of-truth rows. */
@Component
public class EmbeddingMigrationVerifier {
    private final QdrantVectorStore qdrant;
    private final DochubDocumentChunkMapper chunks;
    private final DochubChatMemorySummaryMapper memories;
    private final DochubEmbeddingMigrationDeltaMapper deltas;

    public EmbeddingMigrationVerifier(QdrantVectorStore qdrant, DochubDocumentChunkMapper chunks,
                                      DochubChatMemorySummaryMapper memories,
                                      DochubEmbeddingMigrationDeltaMapper deltas) {
        this.qdrant = qdrant;
        this.chunks = chunks;
        this.memories = memories;
        this.deltas = deltas;
    }

    public void verify(DochubEmbeddingModelMigration job) {
        if (job == null) throw new DochubFrameException(404, "向量迁移任务不存在");
        if (deltas.countPending(job.getId()) != 0) {
            throw new DochubFrameException(409, "仍有待追赶的向量增量");
        }
        requireDimension(job.getTargetDocumentCollection(), job.getTargetDimension());
        requireDimension(job.getTargetMemoryCollection(), job.getTargetDimension());
        long expectedDocuments = chunks.countMigrationSource();
        long expectedMemories = memories.countMigrationSource();
        long actualDocuments = qdrant.count(job.getTargetDocumentCollection());
        long actualMemories = qdrant.count(job.getTargetMemoryCollection());
        if (expectedDocuments != actualDocuments || expectedMemories != actualMemories) {
            throw new DochubFrameException(409, "目标向量数量不一致，拒绝切换");
        }
    }

    private void requireDimension(String collection, Integer expected) {
        int dimension = qdrant.collectionDimension(collection);
        if (expected == null || expected <= 0 || dimension != expected) {
            throw new DochubFrameException(409, "目标集合向量维度不一致，拒绝切换: " + collection);
        }
    }
}
