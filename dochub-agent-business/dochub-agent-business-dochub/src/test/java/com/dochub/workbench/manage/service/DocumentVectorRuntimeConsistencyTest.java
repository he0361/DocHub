package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.service.impl.DefaultDocumentVectorGateway;
import com.dochub.workbench.manage.support.DocumentIndexBuildProgressService;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentVectorRuntimeConsistencyTest {

    @Test
    void vectorOperationKeepsModelAndCollectionFromOneSnapshot() {
        QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
        DocumentIndexBuildProgressService progress = mock(DocumentIndexBuildProgressService.class);
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        EmbeddingModel oldModel = mock(EmbeddingModel.class);
        EmbeddingModel newModel = mock(EmbeddingModel.class);
        EmbeddingRuntimeSnapshot oldRuntime = snapshot(1L, oldModel, 3, "dochub_document_v1", "dochub_memory_v1");
        EmbeddingRuntimeSnapshot newRuntime = snapshot(2L, newModel, 2, "dochub_document_v2", "dochub_memory_v2");
        registry.activateEmbedding(oldRuntime);
        when(oldModel.embed(anyList())).thenAnswer(invocation -> {
            registry.activateEmbedding(newRuntime);
            return List.of(new float[] {1F, 2F, 3F});
        });
        DefaultDocumentVectorGateway gateway = new DefaultDocumentVectorGateway(qdrant, registry, progress);

        DochubDocumentChunk chunk = new DochubDocumentChunk();
        chunk.setId(11L);
        chunk.setDocumentId(21L);
        chunk.setTaskId(31L);
        chunk.setChunkText("atomic runtime");
        gateway.vectorize(List.of(chunk));

        verify(qdrant).ensureDocumentCollection("dochub_document_v1", 3);
        verify(qdrant).upsert(eq("dochub_document_v1"), anyList());
    }

    private EmbeddingRuntimeSnapshot snapshot(long version, EmbeddingModel model, int dimension,
                                               String documents, String memories) {
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", "embedding-" + version,
            null, null, 30_000);
        return new EmbeddingRuntimeSnapshot(version, model, spec, dimension, documents, memories);
    }
}
