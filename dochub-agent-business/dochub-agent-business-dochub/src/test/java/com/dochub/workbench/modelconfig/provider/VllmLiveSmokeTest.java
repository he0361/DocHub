package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Explicitly enabled against an administrator-supplied vLLM endpoint during release verification. */
class VllmLiveSmokeTest {

    @Test
    @EnabledIfSystemProperty(named = "dochub.vllm.live", matches = "true")
    void streamsFromLiveVllm() {
        String baseUrl = System.getProperty("dochub.vllm.base-url");
        String modelName = System.getProperty("dochub.vllm.model");
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT, DeploymentType.LOCAL,
            CompatibilityPreset.OPENAI_COMPATIBLE, baseUrl, "/v1/chat/completions", "/v1/embeddings", "",
            modelName, 0.2, 128, 30_000);
        VllmChatModelProvider provider = new VllmChatModelProvider(new OpenAiCompatibleModelFactory(),
            (model, tools) -> { });

        provider.probe(provider.create(spec), false);
    }
}
