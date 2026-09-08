package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleModelFactoryTest {

    private final OpenAiCompatibleModelFactory factory = new OpenAiCompatibleModelFactory();

    @Test
    void dashscopeDisablesThinking() {
        OpenAiChatOptions options = factory.chatOptions(spec(CompatibilityPreset.DASHSCOPE));

        assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
        assertThat(options.getParallelToolCalls()).isFalse();
    }

    @Test
    void ollamaDisablesThinkingWithoutDashscopeFields() {
        OpenAiChatOptions options = factory.chatOptions(spec(CompatibilityPreset.OLLAMA));

        assertThat(options.getExtraBody()).containsEntry("think", false)
            .doesNotContainKey("enable_thinking");
    }

    @Test
    void genericPresetDoesNotSendProviderPrivateReasoningFields() {
        OpenAiChatOptions options = factory.chatOptions(spec(CompatibilityPreset.OPENAI_COMPATIBLE));

        assertThat(options.getExtraBody()).doesNotContainKeys("enable_thinking", "think");
    }

    @Test
    void configuresBothHttpClientBuildersWithTheCandidateTimeout() {
        RecordingHttpClientBuilders builders = new RecordingHttpClientBuilders();
        OpenAiCompatibleModelFactory factory = new OpenAiCompatibleModelFactory(builders);

        factory.chatModel(spec(CompatibilityPreset.OPENAI_COMPATIBLE));

        assertThat(builders.restTimeout).isEqualTo(Duration.ofMillis(1_000));
        assertThat(builders.webTimeout).isEqualTo(Duration.ofMillis(1_000));
    }

    private ModelRuntimeSpec spec(CompatibilityPreset preset) {
        return new ModelRuntimeSpec(ModelType.CHAT, preset, "https://example.test/v1", "/chat/completions",
            "/embeddings", "api-key", "model-name", 0.2, 128, 1_000);
    }

    private static final class RecordingHttpClientBuilders implements OpenAiHttpClientBuilderFactory {

        private Duration restTimeout;
        private Duration webTimeout;

        @Override
        public RestClient.Builder restClientBuilder(Duration timeout) {
            restTimeout = timeout;
            return RestClient.builder();
        }

        @Override
        public WebClient.Builder webClientBuilder(Duration timeout) {
            webTimeout = timeout;
            return WebClient.builder();
        }
    }
}
