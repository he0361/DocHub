package com.dochub.workbench.chatagent.rag.executor;

import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.rag.model.DirectChatContext;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.rag.service.DirectChatContextService;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.chatagent.service.TaskInfo;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class DirectChatExecutor implements ConversationExecutor {

    private static final String SYSTEM_PROMPT = """
        你是文枢 DocHub 的对话助手。直接、准确地回答用户当前问题。
        不要声称进行了联网搜索，不要虚构时效信息，不要输出内部推理过程。
        对于需要实时信息但未进入搜索模式的问题，明确提示用户选择联网搜索。
        """;

    private final ObservedChatModelService observedChatModel;
    private final DirectChatContextService contextService;
    private final ChatAgentProperties properties;

    public DirectChatExecutor(ObservedChatModelService observedChatModel,
                              DirectChatContextService contextService,
                              ChatAgentProperties properties) {
        this.observedChatModel = observedChatModel;
        this.contextService = contextService;
        this.properties = properties;
    }

    @Override
    public ExecutionMode mode() {
        return ExecutionMode.DIRECT_CHAT;
    }

    @Override
    public Flux<String> execute(TaskInfo taskInfo) {
        DirectChatContext context = contextService.build(
            taskInfo.conversationId(),
            properties.getDirectChatRecentTurns(),
            properties.getDirectChatMaxCharacters()
        );
        String prompt = contextService.render(
            context,
            taskInfo.executionPlan() == null ? taskInfo.question() : taskInfo.executionPlan().getOriginalQuestion(),
            properties.getDirectChatMaxCharacters()
        );
        return observedChatModel.streamText("direct_chat", SYSTEM_PROMPT, prompt, taskInfo.traceRecorder());
    }
}
