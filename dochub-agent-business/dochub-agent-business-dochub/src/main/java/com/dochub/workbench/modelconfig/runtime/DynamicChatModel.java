package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** A primary chat model that delegates each invocation to the currently active snapshot. */
public final class DynamicChatModel implements ChatModel {

    private final ModelRuntimeRegistry registry;

    public DynamicChatModel(ModelRuntimeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        ModelRuntimeSnapshot<ChatModel> snapshot = registry.requireChat();
        return snapshot.model().call(sanitize(prompt, snapshot.spec().compatibilityPreset()));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        ModelRuntimeSnapshot<ChatModel> snapshot = registry.requireChat();
        return snapshot.model().stream(sanitize(prompt, snapshot.spec().compatibilityPreset()));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        // Agent construction asks for options before ApplicationReadyEvent-based
        // configuration activation. An empty OpenAI-compatible option set keeps
        // the management application available until an administrator configures
        // a model; actual chat invocations still require an active snapshot.
        return registry.captureChat()
            .map(snapshot -> snapshot.model().getDefaultOptions())
            .orElseGet(() -> OpenAiChatOptions.builder().build());
    }

    private Prompt sanitize(Prompt prompt, CompatibilityPreset preset) {
        if (!(prompt.getOptions() instanceof OpenAiChatOptions options)) {
            return prompt;
        }
        OpenAiChatOptions sanitized = OpenAiChatOptions.fromOptions(options);
        Map<String, Object> extraBody = options.getExtraBody() == null ? new HashMap<>()
            : new HashMap<>(options.getExtraBody());
        switch (preset) {
            case DASHSCOPE -> {
                extraBody.remove("think");
                extraBody.put("enable_thinking", false);
            }
            case OLLAMA -> {
                extraBody.remove("enable_thinking");
                extraBody.put("think", false);
            }
            case OPENAI_COMPATIBLE -> {
                extraBody.remove("enable_thinking");
                extraBody.remove("think");
            }
        }
        sanitized.setExtraBody(extraBody);
        return new Prompt(prompt.getInstructions(), sanitized);
    }
}
