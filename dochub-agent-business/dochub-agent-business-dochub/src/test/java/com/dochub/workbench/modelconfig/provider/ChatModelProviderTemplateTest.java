package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelProviderTemplateTest {

    private final OpenAiCompatibleModelFactory factory = new OpenAiCompatibleModelFactory();

    @Test
    void localProviderRemovesAnyCredential() {
        VllmChatModelProvider provider = new VllmChatModelProvider(factory, (model, tools) -> { });

        assertThat(provider.normalize(spec(DeploymentType.LOCAL, CompatibilityPreset.OPENAI_COMPATIBLE, "secret"))
            .apiKey()).isEmpty();
    }

    @Test
    void remoteProviderRequiresCredential() {
        RemoteOpenAiChatModelProvider provider = new RemoteOpenAiChatModelProvider(factory, (model, tools) -> { });

        assertThatThrownBy(() -> provider.create(spec(DeploymentType.REMOTE,
            CompatibilityPreset.OPENAI_COMPATIBLE, "")))
            .hasMessageContaining("远程模型必须提供 API Key");
    }

    @Test
    void vllmQwenUsesChatTemplateThinkingSwitch() {
        VllmChatModelProvider provider = new VllmChatModelProvider(factory, (model, tools) -> { });

        assertThat(provider.extraBody(spec(DeploymentType.LOCAL, CompatibilityPreset.OPENAI_COMPATIBLE, "")))
            .containsEntry("chat_template_kwargs", java.util.Map.of("enable_thinking", false));
    }

    private ModelRuntimeSpec spec(DeploymentType deployment, CompatibilityPreset preset, String apiKey) {
        return new ModelRuntimeSpec(ModelType.CHAT, deployment, preset, "http://localhost:8000",
            "/v1/chat/completions", "/v1/embeddings", apiKey, "Qwen3.8-27B", 0.2, 128, 1_000);
    }
}
