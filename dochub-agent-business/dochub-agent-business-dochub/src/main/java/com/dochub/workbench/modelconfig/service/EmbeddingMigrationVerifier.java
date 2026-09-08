package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
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
        throw new IllegalStateException("必须提供候选向量运行时完成迁移校验");
    }

    public void verify(DochubEmbeddingModelMigration job, EmbeddingRuntimeSnapshot target) {
        if (job == null) throw new DochubFrameException(404, "向量迁移任务不存在");
        if (target == null || target.configVersion() != job.getTargetConfigVersion()) {
            throw new DochubFrameException(409, "候选向量运行时版本不匹配");
        }
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
        verifyProvider(target);
        verifySearchProbe(job.getTargetDocumentCollection(), actualDocuments, job.getTargetDimension());
        verifySearchProbe(job.getTargetMemoryCollection(), actualMemories, job.getTargetDimension());
    }

    private void requireDimension(String collection, Integer expected) {
        int dimension = qdrant.collectionDimension(collection);
        if (expected == null || expected <= 0 || dimension != expected) {
            throw new DochubFrameException(409, "目标集合向量维度不一致，拒绝切换: " + collection);
        }
    }

    private void verifyProvider(EmbeddingRuntimeSnapshot target) {
        java.util.List<float[]> first = target.model().embed(java.util.List.of("DocHub embedding migration stability probe"));
        java.util.List<float[]> second = target.model().embed(java.util.List.of("DocHub embedding migration stability probe"));
        float[] left = single(first, target.dimension());
        float[] right = single(second, target.dimension());
        double dot = 0D, leftNorm = 0D, rightNorm = 0D;
        for (int index = 0; index < left.length; index++) {
            dot += left[index] * right[index]; leftNorm += left[index] * left[index]; rightNorm += right[index] * right[index];
        }
        if (leftNorm == 0D || rightNorm == 0D || dot / Math.sqrt(leftNorm * rightNorm) < 0.99D) {
            throw new DochubFrameException(409, "候选向量模型重复探针不稳定，拒绝切换");
        }
    }

    private float[] single(java.util.List<float[]> vectors, int dimension) {
        if (vectors == null || vectors.size() != 1 || vectors.get(0) == null || vectors.get(0).length != dimension) {
            throw new DochubFrameException(409, "候选向量模型探针维度不一致，拒绝切换");
        }
        for (float value : vectors.get(0)) if (!Float.isFinite(value)) {
            throw new DochubFrameException(409, "候选向量模型返回非有限数值，拒绝切换");
        }
        return vectors.get(0);
    }

    private void verifySearchProbe(String collection, long count, int dimension) {
        if (count == 0) return;
        QdrantVectorStore.SamplePoint sample = qdrant.sample(collection);
        if (sample == null || sample.vector() == null || sample.vector().length != dimension) {
            throw new DochubFrameException(409, "目标集合采样校验失败，拒绝切换: " + collection);
        }
        for (float value : sample.vector()) if (!Float.isFinite(value)) {
            throw new DochubFrameException(409, "目标集合样本含非有限向量，拒绝切换: " + collection);
        }
        java.util.List<QdrantVectorStore.SearchHit> hits = qdrant.search(collection, sample.vector(), 1, null);
        if (hits == null || hits.isEmpty() || hits.get(0).id() != sample.id()) {
            throw new DochubFrameException(409, "目标集合搜索探针失败，拒绝切换: " + collection);
        }
    }
}
