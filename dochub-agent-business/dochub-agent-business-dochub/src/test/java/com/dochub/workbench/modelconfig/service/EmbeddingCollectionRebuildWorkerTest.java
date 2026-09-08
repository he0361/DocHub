package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.support.VersionedVectorCollectionNames;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingCollectionRebuildWorkerTest {
    @Test
    void productionConstructorIsExplicitlySelectedForSpringInjection() {
        Constructor<?> productionConstructor = java.util.Arrays.stream(EmbeddingCollectionRebuildWorker.class.getDeclaredConstructors())
            .filter(constructor -> constructor.getParameterCount() == 11)
            .findFirst()
            .orElseThrow();

        assertThat(productionConstructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    void targetCollectionsAreVersionedTogether() {
        VersionedVectorCollectionNames names = VersionedVectorCollectionNames.from("dochub_document", "dochub_memory", 8L);
        assertThat(names.document()).isEqualTo("dochub_document_v8");
        assertThat(names.memory()).isEqualTo("dochub_memory_v8");
    }

    @Test
    void documentBatchUsesCandidateModelAndOnlyTargetCollection() {
        QdrantVectorStore qdrant = mock(QdrantVectorStore.class);
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(List.of("hello"))).thenReturn(List.of(new float[] {1F, 2F, 3F}));
        DochubDocumentChunk chunk = new DochubDocumentChunk();
        chunk.setId(11L); chunk.setDocumentId(2L); chunk.setChunkText("hello");
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setTargetDocumentCollection("document_v8");
        EmbeddingRuntimeSnapshot target = new EmbeddingRuntimeSnapshot(8L, model,
            new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
                "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", "new-model", null, null, 30_000),
            3, "document_v8", "memory_v8");

        EmbeddingCollectionRebuildWorker worker = EmbeddingCollectionRebuildWorker.forVectorWrites(qdrant);
        worker.upsertDocumentBatch(job, target, List.of(chunk));

        verify(qdrant).upsert(eq("document_v8"), argThat(points -> points.size() == 1
            && points.get(0).id() == 11L && points.get(0).vector().length == 3));
    }
}
