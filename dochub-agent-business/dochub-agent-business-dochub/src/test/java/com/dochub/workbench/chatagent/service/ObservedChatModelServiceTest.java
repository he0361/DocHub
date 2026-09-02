package com.dochub.workbench.chatagent.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ObservedChatModelServiceTest {

    @Test
    void observedStreamingCountsOneProviderRequestAndFirstToken() {
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return response("你好");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(response("你"), response("好"));
            }
        };
        ConversationTraceRecorder recorder = new ConversationTraceRecorder(null, null, "conv", 1L, "trace");
        ObservedChatModelService service = new ObservedChatModelService(model);

        assertThat(service.streamText("direct_chat", "system", "question", recorder).collectList().block())
            .containsExactly("你", "好");
        assertThat(recorder.snapshotLatencyTrace().modelCallCount()).isOne();
        assertThat(recorder.snapshotLatencyTrace().promptCharacters()).isEqualTo("system".length() + "question".length());
        assertThat(recorder.snapshotLatencyTrace().timeToFirstTokenMs()).isNotNull();
        assertThat(recorder.snapshotModelUsageTraces()).hasSize(1);
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
