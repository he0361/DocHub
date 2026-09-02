package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.support.EmbeddingCandidateProbe;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingModelChangeServiceTest {

    @Test
    void probeRequiresStableFiniteNonEmptyDimensions() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenReturn(List.of(new float[] {1F, 2F}), List.of(new float[] {1F}));
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(spec -> model);

        assertThatThrownBy(() -> probe.test(spec("model-a"))).hasMessageContaining("维度");
    }

    @Test
    void sameNormalizedModelNameUsesHotSwapMode() {
        assertThat(EmbeddingModelChangeServiceImplSupport.changeMode(" model-a ", "model-a"))
            .isEqualTo("HOT_SWAP");
        assertThat(EmbeddingModelChangeServiceImplSupport.changeMode("model-a", "model-b"))
            .isEqualTo("BLUE_GREEN_REBUILD");
    }

    private ModelRuntimeSpec spec(String name) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", name,
            null, null, 30_000);
    }
}
