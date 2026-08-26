package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ChatModel chatModelReturning(String content) {
        return prompt -> new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    private ModelRuntimeSpec chatSpec() {
        return new ModelRuntimeSpec(ModelType.CHAT, CompatibilityPreset.DASHSCOPE, "https://example.test/v1",
            "/chat/completions", "/embeddings", "api-key", "model-name", 0.2, 128, 1_000);
    }
}
