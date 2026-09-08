package com.dochub.workbench.chatagent.integration;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.model.debug.ChatDebugTrace;
import com.dochub.workbench.chatagent.rag.executor.ConversationExecutorRegistry;
import com.dochub.workbench.chatagent.rag.executor.DirectChatExecutor;
import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;
import com.dochub.workbench.chatagent.rag.model.DirectChatContext;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.service.DirectChatContextService;
import com.dochub.workbench.chatagent.rag.service.OpenChatExecutionRouter;
import com.dochub.workbench.chatagent.service.ConversationTraceRecorder;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.StreamEventMetadata;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.javaup.enums.ChatQueryMode;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenChatFastPathTest {

    @Test
    void ordinaryQuestionUsesOneNonThinkingStreamingRequestAndNoTools() {
        OpenChatExecutionRouter router = new OpenChatExecutionRouter();
        assertThat(router.route("Java 的 record 是什么", null).mode()).isEqualTo(ExecutionMode.DIRECT_CHAT);

        ChatModel model = mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(response("record 是"), response("不可变数据载体")));
        ConversationTraceRecorder recorder = new ConversationTraceRecorder(null, null, "conv", 1L, "trace");
        DirectChatContextService contextService = mock(DirectChatContextService.class);
        when(contextService.build("conv", 6, 12_000)).thenReturn(new DirectChatContext("", List.of(), 0));
        when(contextService.render(any(), eq("Java 的 record 是什么"), eq(12_000))).thenReturn("prompt");
        DirectChatExecutor direct = new DirectChatExecutor(
            new ObservedChatModelService(model), contextService, new ChatAgentProperties());
        ConversationExecutorRegistry registry = new ConversationExecutorRegistry(List.of(direct));

        assertThat(registry.get(ExecutionMode.DIRECT_CHAT).execute(taskInfo(recorder)).collectList().block())
            .containsExactly("record 是", "不可变数据载体");
        recorder.completeLatency();

        verify(model, times(1)).stream(any(Prompt.class));
        assertThat(recorder.snapshotLatencyTrace().modelCallCount()).isOne();
        assertThat(recorder.snapshotLatencyTrace().toolCallCount()).isZero();
        assertThat(recorder.snapshotLatencyTrace().timeToFirstTokenMs()).isNotNull();
        assertThat(new OpenAiCompatibleModelFactory().chatOptions(dashScopeSpec()).getExtraBody())
            .containsEntry("enable_thinking", false);
    }

    private TaskInfo taskInfo(ConversationTraceRecorder recorder) {
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .mode(ExecutionMode.DIRECT_CHAT)
            .chatMode(ChatQueryMode.OPEN_CHAT)
            .originalQuestion("Java 的 record 是什么")
            .agentQuestion("Java 的 record 是什么")
            .build();
        return new TaskInfo(
            "conv", 1L, "Java 的 record 是什么", ChatQueryMode.OPEN_CHAT, "trace",
            null, "", null, LocalDate.now(), "today", plan, ChatDebugTrace.builder().build(),
            RunnableConfig.builder().threadId("conv").build(), recorder,
            Sinks.many().unicast().onBackpressureBuffer(), new StreamEventMetadata("conv", 1L),
            "lease", "owner", new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis()
        );
    }

    private ModelRuntimeSpec dashScopeSpec() {
        return new ModelRuntimeSpec(
            ModelType.CHAT, CompatibilityPreset.DASHSCOPE,
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "/chat/completions", "/embeddings", "key",
            "qwen-plus", 0.2D, 1024, 30_000
        );
    }

    private ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
