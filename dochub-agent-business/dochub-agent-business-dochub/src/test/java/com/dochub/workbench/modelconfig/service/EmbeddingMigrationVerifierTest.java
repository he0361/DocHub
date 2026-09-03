package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingMigrationVerifierTest {
    @Test
    void countMismatchRejectsCandidateBeforeSwitch() {
        QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
        DochubDocumentChunkMapper chunks = mock(DochubDocumentChunkMapper.class);
        DochubChatMemorySummaryMapper memories = mock(DochubChatMemorySummaryMapper.class);
        DochubEmbeddingMigrationDeltaMapper deltas = mock(DochubEmbeddingMigrationDeltaMapper.class);
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(8L); job.setTargetConfigVersion(8L); job.setTargetDimension(3); job.setTargetDocumentCollection("document_v8");
        job.setTargetMemoryCollection("memory_v8");
        when(chunks.countMigrationSource()).thenReturn(2L);
        when(memories.countMigrationSource()).thenReturn(1L);
        when(qdrant.collectionDimension("document_v8")).thenReturn(3);
        when(qdrant.collectionDimension("memory_v8")).thenReturn(3);
        when(qdrant.count("document_v8")).thenReturn(1L);

        EmbeddingMigrationVerifier verifier = new EmbeddingMigrationVerifier(qdrant, chunks, memories, deltas);

        assertThatThrownBy(() -> verifier.verify(job, runtime(mock(EmbeddingModel.class)))).hasMessageContaining("数量不一致");
    }

    @Test
    void rejectsCandidateWhenSeededVectorCannotBeSearchedBack() {
        QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
        DochubDocumentChunkMapper chunks = mock(DochubDocumentChunkMapper.class);
        DochubChatMemorySummaryMapper memories = mock(DochubChatMemorySummaryMapper.class);
        DochubEmbeddingMigrationDeltaMapper deltas = mock(DochubEmbeddingMigrationDeltaMapper.class);
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(List.of("DocHub embedding migration stability probe")))
            .thenReturn(List.of(new float[] {1F, 0F, 0F}), List.of(new float[] {1F, 0F, 0F}));
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(8L); job.setTargetConfigVersion(8L); job.setTargetDimension(3); job.setTargetDocumentCollection("document_v8");
        job.setTargetMemoryCollection("memory_v8");
        when(chunks.countMigrationSource()).thenReturn(1L);
        when(memories.countMigrationSource()).thenReturn(1L);
        when(qdrant.count("document_v8")).thenReturn(1L);
        when(qdrant.count("memory_v8")).thenReturn(1L);
        when(qdrant.collectionDimension("document_v8")).thenReturn(3);
        when(qdrant.collectionDimension("memory_v8")).thenReturn(3);
        when(qdrant.sample("document_v8")).thenReturn(new QdrantVectorStore.SamplePoint(11L,
            new float[] {1F, 0F, 0F}, Map.of()));
        when(qdrant.sample("memory_v8")).thenReturn(new QdrantVectorStore.SamplePoint(22L,
            new float[] {0F, 1F, 0F}, Map.of()));
        when(qdrant.search("document_v8", new float[] {1F, 0F, 0F}, 1, null)).thenReturn(List.of());
        when(qdrant.search("memory_v8", new float[] {0F, 1F, 0F}, 1, null))
            .thenReturn(List.of(new QdrantVectorStore.SearchHit(22L, 1D, Map.of())));

        EmbeddingMigrationVerifier verifier = new EmbeddingMigrationVerifier(qdrant, chunks, memories, deltas);

        assertThatThrownBy(() -> verifier.verify(job, runtime(model))).hasMessageContaining("搜索探针");
    }

    private EmbeddingRuntimeSnapshot runtime(EmbeddingModel model) {
        return new EmbeddingRuntimeSnapshot(8L, model,
            new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
                "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", "model-b", null, null, 30_000),
            3, "document_v8", "memory_v8");
    }
}
