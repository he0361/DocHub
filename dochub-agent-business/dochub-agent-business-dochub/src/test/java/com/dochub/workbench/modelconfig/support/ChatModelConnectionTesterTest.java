package com.dochub.workbench.modelconfig.support;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatModelConnectionTesterTest {
    @Test
    void toolCapableCandidateMustReturnToolCalls() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("plain")))));
        assertThatThrownBy(() -> ChatModelConnectionTester.defaultTester().test(model, true))
            .hasMessageContaining("工具调用协议");
    }

    @Test
    void nonToolCandidateUsesPlainPing() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(java.util.List.of(new Generation(new AssistantMessage("pong")))));
        ChatModelConnectionTester.defaultTester().test(model, false);
        verify(model).call(org.mockito.ArgumentMatchers.argThat((Prompt prompt) -> prompt.getOptions() == null));
    }
}
