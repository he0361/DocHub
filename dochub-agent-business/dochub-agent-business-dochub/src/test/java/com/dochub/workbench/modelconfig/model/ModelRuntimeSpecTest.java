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
}
