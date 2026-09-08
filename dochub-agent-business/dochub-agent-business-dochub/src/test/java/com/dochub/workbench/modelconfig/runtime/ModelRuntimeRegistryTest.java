package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRuntimeRegistryTest {

    @Test
    void newCallsUseActivatedChatSnapshot() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        DynamicChatModel dynamicChatModel = new DynamicChatModel(registry);

        registry.activateChat(1L, chatModelReturning("old"), chatSpec());
        assertThat(dynamicChatModel.call("ping")).isEqualTo("old");

        registry.activateChat(2L, chatModelReturning("new"), chatSpec());
        assertThat(dynamicChatModel.call("ping")).isEqualTo("new");
    }

    @Test
    void requiresAnActivatedChatSnapshot() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();

        assertThatThrownBy(registry::requireChat)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chat");
    }

    @Test
    void defaultOptionsCanBeReadBeforeAChatModelIsConfigured() {
        ChatOptions options = new DynamicChatModel(new ModelRuntimeRegistry()).getDefaultOptions();

        assertThat(options).isInstanceOf(OpenAiChatOptions.class);
    }

    @Test
    void reactAgentCanBeCreatedBeforeAChatModelIsConfigured() {
        assertThatCode(() -> ReactAgent.builder()
            .name("startup-safe-agent")
            .model(new DynamicChatModel(new ModelRuntimeRegistry()))
            .instruction("test")
            .build())
            .doesNotThrowAnyException();
    }

    @Test
    void callOptionsCannotReenableDashscopeThinking() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        AtomicReference<OpenAiChatOptions> receivedOptions = new AtomicReference<>();
        registry.activateChat(1L, prompt -> {
            receivedOptions.set((OpenAiChatOptions) prompt.getOptions());
            return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))));
        }, chatSpec());

        OpenAiChatOptions callOptions = OpenAiChatOptions.builder()
            .extraBody(Map.of("enable_thinking", true))
            .build();
        new DynamicChatModel(registry).call(new Prompt("ping", callOptions));

        assertThat(receivedOptions.get().getExtraBody()).containsEntry("enable_thinking", false);
    }

    @Test
    void streamKeepsTheSnapshotCapturedBeforeSubscription() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        DynamicChatModel dynamicChatModel = new DynamicChatModel(registry);
        registry.activateChat(1L, streamingChatModelReturning("old"), chatSpec());

        Flux<ChatResponse> oldStream = dynamicChatModel.stream(new Prompt("ping"));
        registry.activateChat(2L, streamingChatModelReturning("new"), chatSpec());

        assertThat(oldStream.map(this::content).collectList().block()).containsExactly("old");
    }

    @Test
    void newEmbeddingCallsUseOnlyTheCurrentSnapshot() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        DynamicEmbeddingModel dynamicEmbeddingModel = new DynamicEmbeddingModel(registry);
        registry.activateEmbedding(1L, embeddingModelReturning(new float[]{1.0f}, 1), embeddingSpec());
        registry.activateEmbedding(2L, embeddingModelReturning(new float[]{2.0f, 3.0f}, 2), embeddingSpec());

        EmbeddingResponse response = dynamicEmbeddingModel.call(new EmbeddingRequest(List.of("ping"), null));

        assertThat(response.getResult().getOutput()).containsExactly(2.0f, 3.0f);
        assertThat(dynamicEmbeddingModel.embed(new Document("ping"))).containsExactly(2.0f, 3.0f);
        assertThat(dynamicEmbeddingModel.dimensions()).isEqualTo(2);
    }

    private ChatModel chatModelReturning(String content) {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private ChatModel streamingChatModelReturning(String content) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response(content);
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(response(content));
            }
        };
    }

    private EmbeddingModel embeddingModelReturning(float[] vector, int dimensions) {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                return new EmbeddingResponse(List.of(new Embedding(vector, 0)));
            }

            @Override
            public float[] embed(Document document) {
                return vector;
            }

            @Override
            public int dimensions() {
                return dimensions;
            }
        };
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private String content(ChatResponse response) {
        return response.getResult().getOutput().getText();
    }

    private ModelRuntimeSpec chatSpec() {
        return new ModelRuntimeSpec(ModelType.CHAT, CompatibilityPreset.DASHSCOPE, "https://example.test/v1",
            "/chat/completions", "/embeddings", "api-key", "model-name", 0.2, 128, 1_000);
    }

    private ModelRuntimeSpec embeddingSpec() {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "https://example.test/v1", "/chat/completions", "/embeddings", "api-key", "embedding-model",
            null, null, 1_000);
    }
}
