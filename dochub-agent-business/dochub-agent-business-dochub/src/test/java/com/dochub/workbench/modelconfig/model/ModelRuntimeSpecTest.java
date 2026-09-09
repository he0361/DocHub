package com.dochub.workbench.modelconfig.model;

import com.dochub.workbench.modelconfig.runtime.ModelRuntimeSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRuntimeSpecTest {

    @Test
    void stringRepresentationsNeverExposeTheDecryptedApiKey() {
        String secret = "super-secret-api-key";
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT, CompatibilityPreset.OPENAI_COMPATIBLE,
            "https://example.test/v1", "/chat/completions", "/embeddings", secret, "model-name", null,
            null, 1_000);
        ModelRuntimeSnapshot<String> snapshot = new ModelRuntimeSnapshot<>(1L, "model", spec);

        assertThat(spec.toString()).doesNotContain(secret).contains("apiKey=<redacted>");
        assertThat(snapshot.toString()).doesNotContain(secret).contains("apiKey=<redacted>");
    }

    @Test
    void requestPathsAreNormalizedWithOneLeadingSlash() {
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.DASHSCOPE,
            "https://dashscope.aliyuncs.com/compatible-mode/", "v1/chat/completions", "///v1/embeddings",
            "secret", "text-embedding-v4", null, null, 30_000);

        assertThat(spec.baseUrl()).isEqualTo("https://dashscope.aliyuncs.com/compatible-mode");
        assertThat(spec.completionsPath()).isEqualTo("/v1/chat/completions");
        assertThat(spec.embeddingsPath()).isEqualTo("/v1/embeddings");
    }
}
