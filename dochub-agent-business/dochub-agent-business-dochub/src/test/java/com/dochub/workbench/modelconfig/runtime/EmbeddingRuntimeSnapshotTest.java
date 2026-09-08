package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddingRuntimeSnapshotTest {

    @Test
    void activatesModelDimensionAndBothCollectionsAsOneSnapshot() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        EmbeddingModel model = mock(EmbeddingModel.class);
        ModelRuntimeSpec spec = spec("text-embedding-v2");

        registry.activateEmbedding(new EmbeddingRuntimeSnapshot(
            8L, model, spec, 1024, "dochub_document_v8", "dochub_memory_v8"));

        EmbeddingRuntimeSnapshot active = registry.captureEmbedding();
        assertThat(active.configVersion()).isEqualTo(8L);
        assertThat(active.model()).isSameAs(model);
        assertThat(active.spec()).isSameAs(spec);
        assertThat(active.dimension()).isEqualTo(1024);
        assertThat(active.documentCollection()).isEqualTo("dochub_document_v8");
        assertThat(active.memoryCollection()).isEqualTo("dochub_memory_v8");
    }

    private ModelRuntimeSpec spec(String modelName) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://127.0.0.1:11434", "/v1/chat/completions", "/v1/embeddings", "",
            modelName, null, null, 30_000);
    }
}
