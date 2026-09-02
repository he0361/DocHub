package com.dochub.workbench.chatagent.rag.executor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageCode;
import com.dochub.workbench.chatagent.rag.model.AgentTurnContext;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.service.AgentTurnContextFactory;
import com.dochub.workbench.chatagent.rag.support.ExecutorEventSupport;
import com.dochub.workbench.chatagent.service.ChatCheckpointManager;
import com.dochub.workbench.chatagent.service.ConversationTraceRecorder;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.StreamEventWriter;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: ReactAgent 执行器
 * @author: zhangjihe
 **/

@Component
@Slf4j
public class ReactAgentExecutor implements ConversationExecutor {

    private final ReactAgent reactAgent;
    private final StreamEventWriter streamEventWriter;
    private final AgentTurnContextFactory turnContextFactory;
    private final ChatCheckpointManager checkpointManager;

    public ReactAgentExecutor(ReactAgent businessChatReactAgent,
                              StreamEventWriter streamEventWriter,
                              AgentTurnContextFactory turnContextFactory,
                              ChatCheckpointManager checkpointManager) {
        this.reactAgent = businessChatReactAgent;
        this.streamEventWriter = streamEventWriter;
        this.turnContextFactory = turnContextFactory;
        this.checkpointManager = checkpointManager;
    }

    @Override
    public ExecutionMode mode() {
        return ExecutionMode.REACT_AGENT;
    }

    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        AtomicBoolean streamedText = new AtomicBoolean(false);
        AgentTurnContext turnContext = turnContextFactory.create(
            taskInfo.conversationId(), taskInfo.exchangeId(), taskInfo.runnableConfig());
        taskInfo.setActiveAgentConfig(turnContext.runnableConfig());
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前问题进入开放式 Agent 自主执行阶段。");

        taskInfo.debugTrace().getRetrievalNotes().add("当前问题走 ReactAgent 执行路径，由 Agent 自主决定是否调用联网搜索或其他工具。");
        ConversationTraceRecorder.StageHandle agentStage = taskInfo.traceRecorder() == null
            ? null
            : taskInfo.traceRecorder().startStage(
                ConversationTraceStageCode.REACT_AGENT,
                mode().name(),
                "正在执行 ReAct Agent 推理与工具调用。",
                null
            );
        try {
            return reactAgent.stream(
                    turnContext.prependTo(taskInfo.executionPlan().getAgentQuestion()),
                    turnContext.runnableConfig())
                .publishOn(Schedulers.boundedElastic())
                .concatMap(output -> extractTextChunk(output, streamedText))
                .doOnComplete(() -> {
                    if (taskInfo.traceRecorder() != null) {
                        taskInfo.traceRecorder().completeStage(agentStage, "ReAct Agent 执行完成。", Map.of(
                            "toolNames", taskInfo.debugTrace().getToolTraces() == null ? List.of() : taskInfo.debugTrace().getToolTraces(),
                            "usedTools", taskInfo.usedTools() == null ? List.of() : taskInfo.usedTools()
                        ));
                    }
                })
                .doOnError(error -> {
                    if (taskInfo.traceRecorder() != null) {
                        taskInfo.traceRecorder().failStage(agentStage, "ReAct Agent 执行失败。", error.getMessage(), null);
                    }
                })
                .doFinally(signal -> cleanupTurn(taskInfo, turnContext));
        }
        catch (GraphRunnerException exception) {

            if (taskInfo.traceRecorder() != null) {
                taskInfo.traceRecorder().failStage(agentStage, "ReAct Agent 执行失败。", exception.getMessage(), null);
            }
            cleanupTurn(taskInfo, turnContext);
            return Flux.error(exception);
        }
    }

    private void cleanupTurn(TaskInfo taskInfo, AgentTurnContext turnContext) {
        taskInfo.setActiveAgentConfig(null);
        try {
            checkpointManager.clearThread(turnContext.threadId());
        }
        catch (RuntimeException exception) {
            log.warn("清理 ReAct 子线程检查点失败。threadId={}, error={}",
                turnContext.threadId(), exception.getMessage());
        }
    }

    private Mono<String> extractTextChunk(NodeOutput output, AtomicBoolean streamedText) {
        if (!(output instanceof StreamingOutput<?> streamingOutput)) {

            return Mono.empty();
        }

        String content = extractStreamingText(streamingOutput);
        if (StrUtil.isBlank(content)) {
            return Mono.empty();
        }

        if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {

            streamedText.set(true);
            return Mono.just(content);
        }

        if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {

            if (streamedText.get()) {
                return Mono.empty();
            }
            return Mono.just(content);
        }

        return Mono.empty();
    }

    private String extractStreamingText(StreamingOutput<?> streamingOutput) {
        Message message = streamingOutput.message();
        if (message != null && StrUtil.isNotBlank(message.getText())) {
            return message.getText();
        }

        Object originData = streamingOutput.getOriginData();
        if (originData instanceof Message originMessage && StrUtil.isNotBlank(originMessage.getText())) {
            return originMessage.getText();
        }
        if (originData instanceof String text && StrUtil.isNotBlank(text)) {
            return text;
        }
        return "";
    }
}
