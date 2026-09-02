package com.dochub.workbench.chatagent.rag.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.rag.config.ChatRagProperties;
import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;
import com.dochub.workbench.chatagent.rag.model.ExecutionMode;
import com.dochub.workbench.chatagent.service.ConversationMemoryService;
import com.dochub.workbench.chatagent.service.TaskInfo;
import com.dochub.workbench.chatagent.support.ChatContextKeys;
import com.dochub.workbench.manage.service.DocumentKnowledgeService;
import com.dochub.workbench.manage.service.KnowledgeRouteService;
import org.javaup.enums.ChatQueryMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ChatPreparationOrchestratorOpenChatTest {

    @Test
    void ordinaryOpenChatRoutesBeforeMemoryRewriteAndKnowledgeServices() {
        ConversationMemoryService memory = mock(ConversationMemoryService.class);
        AnswerHistoryContextAssembler history = mock(AnswerHistoryContextAssembler.class);
        ChatQueryRewriteService rewrite = mock(ChatQueryRewriteService.class);
        DocumentQuestionRouter documentRouter = mock(DocumentQuestionRouter.class);
        KnowledgeRouteService knowledgeRoute = mock(KnowledgeRouteService.class);
        DocumentKnowledgeService documentKnowledge = mock(DocumentKnowledgeService.class);
        ChatPreparationOrchestrator orchestrator = new ChatPreparationOrchestrator(
            mock(ChatRagProperties.class), new ChatAgentProperties(), memory, history, rewrite,
            documentRouter, knowledgeRoute, documentKnowledge, new OpenChatExecutionRouter());

        ConversationExecutionPlan plan = orchestrator.prepare(task("Java record 是什么", null));

        assertThat(plan.getMode()).isEqualTo(ExecutionMode.DIRECT_CHAT);
        assertThat(plan.getAgentQuestion()).isEqualTo("Java record 是什么");
        assertThat(plan.getRouteReason()).isEqualTo("ORDINARY_OPEN_CHAT");
        verifyNoInteractions(memory, history, rewrite, documentRouter, knowledgeRoute, documentKnowledge);
    }

    @Test
    void explicitSearchStillUsesReact() {
        ChatPreparationOrchestrator orchestrator = new ChatPreparationOrchestrator(
            mock(ChatRagProperties.class), new ChatAgentProperties(), mock(ConversationMemoryService.class),
            mock(AnswerHistoryContextAssembler.class), mock(ChatQueryRewriteService.class),
            mock(DocumentQuestionRouter.class), mock(KnowledgeRouteService.class),
            mock(DocumentKnowledgeService.class), new OpenChatExecutionRouter());

        ConversationExecutionPlan plan = orchestrator.prepare(task("搜索今天 AI 新闻", null));

        assertThat(plan.getMode()).isEqualTo(ExecutionMode.REACT_AGENT);
    }

    private TaskInfo task(String question, String requestedMode) {
        RunnableConfig config = RunnableConfig.builder().threadId("conv").build();
        if (requestedMode != null) {
            config.context().put(ChatContextKeys.OPEN_CHAT_MODE, requestedMode);
        }
        return new TaskInfo(
            "conv", 1L, question, ChatQueryMode.OPEN_CHAT, "trace", null, "", null,
            LocalDate.now(), "today", null, null, config, null, null, null,
            "lease", "owner", new ArrayList<>(), new ArrayList<>(), new HashSet<>(), System.currentTimeMillis()
        );
    }
}
