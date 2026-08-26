package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Map;
import java.util.Objects;
import java.time.Duration;

/** Builds provider-neutral OpenAI-compatible models from a decrypted runtime spec. */
public final class OpenAiCompatibleModelFactory {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final OpenAiHttpClientBuilderFactory httpClientBuilders;

    public OpenAiCompatibleModelFactory() {
        this(OpenAiHttpClientBuilderFactory.defaults());
    }

    OpenAiCompatibleModelFactory(OpenAiHttpClientBuilderFactory httpClientBuilders) {
        this.httpClientBuilders = Objects.requireNonNull(httpClientBuilders, "httpClientBuilders must not be null");
    }

    public ChatModel chatModel(ModelRuntimeSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return OpenAiChatModel.builder()
            .openAiApi(openAiApi(spec))
            .defaultOptions(chatOptions(spec))
            .build();
    }

    public EmbeddingModel embeddingModel(ModelRuntimeSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder().model(spec.modelName()).build();
        return new OpenAiEmbeddingModel(openAiApi(spec), MetadataMode.NONE, options);
    }

    public OpenAiChatOptions chatOptions(ModelRuntimeSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
            .model(spec.modelName())
            .parallelToolCalls(false);
        if (spec.temperature() != null) {
            builder.temperature(spec.temperature());
        }
        if (spec.maxTokens() != null) {
            builder.maxTokens(spec.maxTokens());
        }
        Map<String, Object> extraBody = switch (spec.compatibilityPreset()) {
            case DASHSCOPE -> Map.of("enable_thinking", false);
            case OLLAMA -> Map.of("think", false);
            case OPENAI_COMPATIBLE -> Map.of();
        };
        return builder.extraBody(extraBody).build();
    }

    private OpenAiApi openAiApi(ModelRuntimeSpec spec) {
        Duration timeout = spec.timeoutMillis() == null ? DEFAULT_TIMEOUT : Duration.ofMillis(spec.timeoutMillis());
        return OpenAiApi.builder()
            .baseUrl(spec.baseUrl())
            .apiKey(spec.apiKey())
            .completionsPath(spec.completionsPath())
            .embeddingsPath(spec.embeddingsPath())
            .restClientBuilder(httpClientBuilders.restClientBuilder(timeout))
            .webClientBuilder(httpClientBuilders.webClientBuilder(timeout))
            .build();
    }
}
