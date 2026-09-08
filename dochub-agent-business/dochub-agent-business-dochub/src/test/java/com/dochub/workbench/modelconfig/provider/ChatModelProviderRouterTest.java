package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static com.dochub.workbench.modelconfig.model.CompatibilityPreset.DASHSCOPE;
import static com.dochub.workbench.modelconfig.model.CompatibilityPreset.OLLAMA;
import static com.dochub.workbench.modelconfig.model.CompatibilityPreset.OPENAI_COMPATIBLE;
import static com.dochub.workbench.modelconfig.model.DeploymentType.LOCAL;
import static com.dochub.workbench.modelconfig.model.DeploymentType.REMOTE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatModelProviderRouterTest {

    @Test
    void routesEverySupportedDeploymentAndPresetPair() {
        ChatModelProvider vllm = provider(LOCAL, OPENAI_COMPATIBLE);
        ChatModelProvider ollama = provider(LOCAL, OLLAMA);
        ChatModelProvider remoteOpenAi = provider(REMOTE, OPENAI_COMPATIBLE);
        ChatModelProvider dashScope = provider(REMOTE, DASHSCOPE);
        ChatModelProviderRouter router = new ChatModelProviderRouter(List.of(vllm, ollama, remoteOpenAi, dashScope));

        assertThat(router.requireProvider(spec(LOCAL, OPENAI_COMPATIBLE))).isSameAs(vllm);
        assertThat(router.requireProvider(spec(LOCAL, OLLAMA))).isSameAs(ollama);
        assertThat(router.requireProvider(spec(REMOTE, OPENAI_COMPATIBLE))).isSameAs(remoteOpenAi);
        assertThat(router.requireProvider(spec(REMOTE, DASHSCOPE))).isSameAs(dashScope);
    }

    @Test
    void rejectsUnsupportedPair() {
        ChatModelProviderRouter router = new ChatModelProviderRouter(List.of(provider(REMOTE, DASHSCOPE)));

        assertThatThrownBy(() -> router.requireProvider(spec(LOCAL, DASHSCOPE)))
            .hasMessageContaining("不支持的模型部署与兼容预设组合");
    }

    private ChatModelProvider provider(DeploymentType deploymentType, CompatibilityPreset preset) {
        return new ChatModelProvider() {
            @Override
            public boolean supports(DeploymentType candidateDeployment, CompatibilityPreset candidatePreset) {
                return deploymentType == candidateDeployment && preset == candidatePreset;
            }

            @Override
            public ChatModel create(ModelRuntimeSpec spec) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void probe(ChatModel model, boolean toolCallingSupported) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private ModelRuntimeSpec spec(DeploymentType deploymentType, CompatibilityPreset preset) {
        return new ModelRuntimeSpec(ModelType.CHAT, deploymentType, preset, "http://localhost:8000",
            "/v1/chat/completions", "/v1/embeddings", "", "model", 0.2, 128, 1_000);
    }
}
