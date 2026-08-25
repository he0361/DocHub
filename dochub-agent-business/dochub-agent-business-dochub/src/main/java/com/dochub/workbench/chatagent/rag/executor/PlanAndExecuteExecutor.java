package com.dochub.workbench.chatagent.rag.executor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageCode;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.model.PlanStep;
import com.dochub.workbench.chatagent.rag.support.ExecutorEventSupport;
import com.dochub.workbench.chatagent.service.ConversationTraceRecorder;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.StreamEventWriter;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 计划-执行（Plan-and-Execute）执行器。
 *
 * <p>适用于需要多步骤推理、对比分析、方案设计的复杂开放式问题。执行流程：
 * <ol>
 *   <li><b>规划</b>：Planner 把用户目标拆成若干有序执行步骤（JSON，受 {@code plan-max-steps} 约束）；</li>
 *   <li><b>逐步执行</b>：每个步骤交给带工具的 {@link ReactAgent} 自主执行（可联网搜索），步骤间共用同一会话
 *       线程，从而受 {@code max-model-calls-per-thread} 的全局预算约束；</li>
 *   <li><b>汇总</b>：把各步骤结果交给 LLM 综合成最终回答。</li>
 * </ol>
 * 预算护栏：步骤数上限 + 每步 ReAct 的 run/thread 模型调用与工具调用上限 + 计划/汇总各一次调用，
 * 保证不会死循环烧 token。规划失败时退化为单步直接执行。</p>
 */
@Slf4j
@Component
public class PlanAndExecuteExecutor implements ConversationExecutor {

    /** 单步提示词中目标上下文的最大长度，避免把长问题原样塞进每一步 */
    private static final int STEP_GOAL_MAX_CHARS = 200;

    private final ChatModel chatModel;
    private final ReactAgent reactAgent;
    private final PromptTemplateService promptTemplateService;
    private final StreamEventWriter streamEventWriter;
    private final ChatAgentProperties properties;
    private final ObjectMapper objectMapper;

    public PlanAndExecuteExecutor(ChatModel chatModel,
                                  ReactAgent businessChatReactAgent,
                                  PromptTemplateService promptTemplateService,
                                  StreamEventWriter streamEventWriter,
                                  ChatAgentProperties properties,
                                  ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.reactAgent = businessChatReactAgent;
        this.promptTemplateService = promptTemplateService;
        this.streamEventWriter = streamEventWriter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ExecutionMode mode() {
        return ExecutionMode.PLAN_AND_EXECUTE;
    }

    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        ConversationTraceRecorder traceRecorder = taskInfo.traceRecorder();
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "当前问题进入「计划-执行」模式：先生成执行计划，再逐步执行并汇总。");
        ConversationTraceRecorder.StageHandle planStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.PLAN, mode().name(), "正在生成执行计划。", null);

        return Mono.fromCallable(() -> planSteps(taskInfo))
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(steps -> {
                if (traceRecorder != null) {
                    traceRecorder.completeStage(planStage, "计划生成完成。",
                        Map.of("stepCount", steps.size(), "plan", joinStepTitles(steps)));
                }
            })
            .flatMapMany(steps -> {
                if (steps.isEmpty()) {
                    // 规划失败：把原问题当作单步直接执行，退化为 ReAct
                    return executeStep(taskInfo, null, goalOf(taskInfo));
                }
                ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter,
                    "已生成 " + steps.size() + " 步执行计划：" + joinStepTitles(steps));
                return Flux.concat(
                    Flux.range(0, steps.size())
                        .concatMap(index -> executeStep(taskInfo, steps.get(index), index + 1, steps.size())),
                    Mono.fromCallable(() -> synthesize(taskInfo, steps))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMapMany(text -> StrUtil.isBlank(text) ? Flux.empty() : Flux.just(text))
                );
            });
    }

    // ===== 规划 =====

    private List<PlanStep> planSteps(TaskInfo taskInfo) {
        String goal = goalOf(taskInfo);
        int maxSteps = Math.max(1, properties.getPlanMaxSteps());
        try {
            String prompt = promptTemplateService.render(PromptTemplateNames.PLAN_EXECUTE_PLANNER, Map.of(
                "goal", StrUtil.blankToDefault(goal, ""),
                "maxSteps", String.valueOf(maxSteps)));
            String content = ChatClient.builder(chatModel).build().prompt().user(prompt).call().content();
            List<PlanStep> steps = parseSteps(content, maxSteps);
            if (!steps.isEmpty()) {
                log.info("计划-执行：计划生成成功。goal='{}', stepCount={}", goal, steps.size());
                return steps;
            }
            log.warn("计划-执行：计划解析为空，退化为单步执行。goal='{}'", goal);
            return List.of();
        }
        catch (Exception exception) {
            log.warn("计划-执行：计划生成失败，退化为单步执行。goal='{}', error={}", goal, exception.getMessage());
            return List.of();
        }
    }

    private List<PlanStep> parseSteps(String content, int maxSteps) {
        if (StrUtil.isBlank(content)) {
            return List.of();
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(content.substring(start, end + 1));
            JsonNode stepsNode = root.path("steps");
            if (!stepsNode.isArray()) {
                return List.of();
            }
            List<PlanStep> steps = new ArrayList<>();
            for (JsonNode node : stepsNode) {
                String title = node.path("title").asText("").trim();
                if (StrUtil.isBlank(title)) {
                    continue;
                }
                steps.add(new PlanStep(title));
                if (steps.size() >= maxSteps) {
                    break;
                }
            }
            return steps;
        }
        catch (Exception exception) {
            log.warn("计划-执行：解析计划 JSON 失败。error={}", exception.getMessage());
            return List.of();
        }
    }

    // ===== 步骤执行 =====

    private Flux<String> executeStep(TaskInfo taskInfo, PlanStep step, int index, int total) {
        ConversationTraceRecorder traceRecorder = taskInfo.traceRecorder();
        String label = "步骤 " + index + "/" + total + "：" + step.getTitle();
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在执行" + label);
        ConversationTraceRecorder.StageHandle stepStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.STEP_EXECUTE, mode().name(), label, null);
        AtomicBoolean streamedText = new AtomicBoolean(false);
        try {
            return reactAgent.stream(buildStepPrompt(taskInfo, step), taskInfo.runnableConfig())
                .publishOn(Schedulers.boundedElastic())
                .concatMap(output -> extractTextChunk(output, streamedText))
                .doOnNext(text -> step.appendResult(text))
                .doOnComplete(() -> {
                    if (traceRecorder != null) {
                        traceRecorder.completeStage(stepStage, "步骤执行完成。", Map.of("step", step.getTitle()));
                    }
                })
                .doOnError(error -> {
                    if (traceRecorder != null) {
                        traceRecorder.failStage(stepStage, "步骤执行失败。", error.getMessage(), Map.of("step", step.getTitle()));
                    }
                    log.warn("计划-执行：步骤执行失败，跳过继续。step={}, error={}", step.getTitle(), error.getMessage());
                })
                .onErrorResume(error -> Flux.empty());
        }
        catch (GraphRunnerException exception) {
            log.warn("计划-执行：启动步骤执行失败。step={}, error={}", step.getTitle(), exception.getMessage());
            return Flux.empty();
        }
    }

    private Flux<String> executeStep(TaskInfo taskInfo, PlanStep step, String fallbackPrompt) {
        ConversationTraceRecorder traceRecorder = taskInfo.traceRecorder();
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "正在执行（计划未生成，按单步执行）。");
        ConversationTraceRecorder.StageHandle stepStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.STEP_EXECUTE, mode().name(), "整体回答", null);
        AtomicBoolean streamedText = new AtomicBoolean(false);
        try {
            return reactAgent.stream(fallbackPrompt, taskInfo.runnableConfig())
                .publishOn(Schedulers.boundedElastic())
                .concatMap(output -> extractTextChunk(output, streamedText))
                .doOnComplete(() -> {
                    if (traceRecorder != null) {
                        traceRecorder.completeStage(stepStage, "整体回答执行完成。", null);
                    }
                })
                .doOnError(error -> {
                    if (traceRecorder != null) {
                        traceRecorder.failStage(stepStage, "整体回答执行失败。", error.getMessage(), null);
                    }
                    log.warn("计划-执行：整体回答执行失败。error={}", error.getMessage());
                })
                .onErrorResume(error -> Flux.empty());
        }
        catch (GraphRunnerException exception) {
            log.warn("计划-执行：启动整体回答执行失败。error={}", exception.getMessage());
            return Flux.empty();
        }
    }

    private String buildStepPrompt(TaskInfo taskInfo, PlanStep step) {
        String goal = truncate(goalOf(taskInfo), STEP_GOAL_MAX_CHARS);
        return "你在完成以下目标（这是整体任务，你只负责其中一步）：\n目标：" + goal + "\n\n"
            + "请执行这一步（只做这一步，不要自行扩展成别的步骤）：\n" + step.getTitle() + "\n\n"
            + "如需最新信息、事实资料或网页来源，可以调用 tavily_search 工具。完成本步骤后直接输出这一步的结果。";
    }

    // ===== 汇总 =====

    private String synthesize(TaskInfo taskInfo, List<PlanStep> steps) {
        ConversationTraceRecorder traceRecorder = taskInfo.traceRecorder();
        ExecutorEventSupport.publishThinking(taskInfo, streamEventWriter, "各步骤执行完成，正在汇总最终回答。");
        ConversationTraceRecorder.StageHandle finalStage = traceRecorder == null
            ? null
            : traceRecorder.startStage(ConversationTraceStageCode.FINAL, mode().name(), "正在汇总最终回答。", null);
        try {
            StringBuilder results = new StringBuilder();
            for (int index = 0; index < steps.size(); index++) {
                PlanStep step = steps.get(index);
                results.append("【步骤").append(index + 1).append("】").append(step.getTitle()).append("：\n")
                    .append(StrUtil.isBlank(step.getResult()) ? "（该步骤未产出有效结果）" : step.getResult())
                    .append("\n\n");
            }
            String prompt = promptTemplateService.render(PromptTemplateNames.PLAN_EXECUTE_FINAL, Map.of(
                "goal", StrUtil.blankToDefault(goalOf(taskInfo), ""),
                "stepResults", results.toString()));
            String content = ChatClient.builder(chatModel).build().prompt().user(prompt).call().content();
            if (traceRecorder != null) {
                traceRecorder.completeStage(finalStage, "汇总完成。", Map.of("stepCount", steps.size()));
            }
            return StrUtil.blankToDefault(content, "");
        }
        catch (Exception exception) {
            if (traceRecorder != null) {
                traceRecorder.failStage(finalStage, "汇总失败。", exception.getMessage(), null);
            }
            log.warn("计划-执行：最终汇总失败。error={}", exception.getMessage());
            return "";
        }
    }

    // ===== 工具方法 =====

    private String goalOf(TaskInfo taskInfo) {
        return taskInfo.executionPlan() == null
            ? StrUtil.blankToDefault(taskInfo.question(), "")
            : StrUtil.blankToDefault(taskInfo.executionPlan().getAgentQuestion(), "");
    }

    private String truncate(String text, int maxChars) {
        String trimmed = StrUtil.blankToDefault(text, "").replaceAll("\\s+", " ").trim();
        return trimmed.length() > maxChars ? trimmed.substring(0, maxChars) : trimmed;
    }

    private String joinStepTitles(List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return "";
        }
        return steps.stream().map(PlanStep::getTitle).collect(Collectors.joining("；"));
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
