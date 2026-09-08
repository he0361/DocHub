package com.dochub.workbench.chatagent.rag.executor;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;
import com.dochub.workbench.chatagent.rag.model.DirectChatContext;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.service.DirectChatContextService;
import com.dochub.workbench.chatagent.service.ConversationTraceRecorder;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.StreamEventMetadata;
import org.javaup.enums.ChatQueryMode;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class DirectChatExecutorTest {

    @Test
    void streamsThroughExactlyOneObservedModelCall() {
        DirectChatContextService contextService = mock(DirectChatContextService.class);
        ObservedChatModelService observed = mock(ObservedChatModelService.class);
        ChatAgentProperties properties = new ChatAgentProperties();
        when(contextService.build("conv", 6, 12_000)).thenReturn(new DirectChatContext("", List.of(), 0));
        when(contextService.render(any(), eq("Java record 是什么"), eq(12_000))).thenReturn("prompt");
        when(observed.streamText(eq("direct_chat"), any(), eq("prompt"), any()))
            .thenReturn(Flux.just("你", "好"));
        DirectChatExecutor executor = new DirectChatExecutor(observed, contextService, properties);

        assertThat(executor.execute(taskInfo()).collectList().block()).containsExactly("你", "好");

        verify(observed, times(1)).streamText(eq("direct_chat"), any(), eq("prompt"), any());
    }

    private TaskInfo taskInfo() {
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .mode(ExecutionMode.DIRECT_CHAT)
            .chatMode(ChatQueryMode.OPEN_CHAT)
            .originalQuestion("Java record 是什么")
            .build();
        return new TaskInfo(
            "conv", 1L, "Java record 是什么", ChatQueryMode.OPEN_CHAT, "trace",
            null, "", null, LocalDate.now(), "today", plan, null,
            RunnableConfig.builder().threadId("conv").build(),
            new ConversationTraceRecorder(null, null, "conv", 1L, "trace"),
            Sinks.many().unicast().onBackpressureBuffer(), new StreamEventMetadata("conv", 1L),
            "lease", "owner", new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis()
        );
    }
}
