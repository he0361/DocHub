package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.chatagent.mapper.DochubChatMemorySummaryMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentChunkMapper;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import org.junit.jupiter.api.Test;

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
        job.setId(8L); job.setTargetDimension(3); job.setTargetDocumentCollection("document_v8");
        job.setTargetMemoryCollection("memory_v8");
        when(chunks.countMigrationSource()).thenReturn(2L);
        when(memories.countMigrationSource()).thenReturn(1L);
        when(qdrant.collectionDimension("document_v8")).thenReturn(3);
        when(qdrant.collectionDimension("memory_v8")).thenReturn(3);
        when(qdrant.count("document_v8")).thenReturn(1L);

        EmbeddingMigrationVerifier verifier = new EmbeddingMigrationVerifier(qdrant, chunks, memories, deltas);

        assertThatThrownBy(() -> verifier.verify(job)).hasMessageContaining("数量不一致");
    }
}
