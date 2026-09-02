package com.dochub.workbench.chatagent.rag.executor;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.dochub.workbench.chatagent.model.debug.ChatDebugTrace;
import com.dochub.workbench.chatagent.rag.model.AgentTurnContext;
import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.service.AgentTurnContextFactory;
import com.dochub.workbench.chatagent.service.ChatCheckpointManager;
import com.dochub.workbench.chatagent.service.ConversationTraceRecorder;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.StreamEventMetadata;
import com.dochub.workbench.chatagent.support.StreamEventWriter;
import org.javaup.enums.ChatQueryMode;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.when;

class ReactAgentExecutorTest {

    @Test
    void usesIndependentBoundedTurnContextAndCleansItAfterCompletion() throws Exception {
        ReactAgent reactAgent = mock(ReactAgent.class);
        StreamEventWriter eventWriter = mock(StreamEventWriter.class);
        when(eventWriter.thinking(anyString(), any())).thenReturn("{}");
        AgentTurnContextFactory contextFactory = mock(AgentTurnContextFactory.class);
        ChatCheckpointManager checkpointManager = mock(ChatCheckpointManager.class);
        RunnableConfig childConfig = RunnableConfig.builder().threadId("conv:exchange:9").build();
        when(reactAgent.stream("历史用户：上一问\n历史助手：上一答\n\n【当前任务】\n当前问题", childConfig))
            .thenReturn(Flux.empty());
        ReactAgentExecutor executor = new ReactAgentExecutor(reactAgent, eventWriter, contextFactory, checkpointManager);
        TaskInfo taskInfo = taskInfo();
        when(contextFactory.create("conv", 9L, taskInfo.runnableConfig()))
            .thenReturn(new AgentTurnContext("conv:exchange:9", childConfig, List.of("历史用户：上一问", "历史助手：上一答")));

        executor.execute(taskInfo).collectList().block();

        verify(reactAgent).stream("历史用户：上一问\n历史助手：上一答\n\n【当前任务】\n当前问题", childConfig);
        verify(checkpointManager, timeout(1_000)).clearThread("conv:exchange:9");
    }

    private TaskInfo taskInfo() {
        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .mode(ExecutionMode.REACT_AGENT)
            .chatMode(ChatQueryMode.OPEN_CHAT)
            .originalQuestion("当前问题")
            .agentQuestion("当前问题")
            .build();
        return new TaskInfo(
            "conv", 9L, "当前问题", ChatQueryMode.OPEN_CHAT, "trace",
            null, "", null, LocalDate.now(), "today", plan, ChatDebugTrace.builder().build(),
            RunnableConfig.builder().threadId("conv").build(),
            null,
            Sinks.many().unicast().onBackpressureBuffer(), new StreamEventMetadata("conv", 9L),
            "lease", "owner", new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis()
        );
    }
}
