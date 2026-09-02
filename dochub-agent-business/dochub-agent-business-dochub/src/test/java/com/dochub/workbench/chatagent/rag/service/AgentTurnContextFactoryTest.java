package com.dochub.workbench.chatagent.rag.service;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dochub.workbench.chatagent.model.ConversationExchangeView;
import com.dochub.workbench.chatagent.model.memory.ConversationMemoryContext;
import com.dochub.workbench.chatagent.rag.model.AgentTurnContext;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.service.ConversationArchiveStore;
import com.dochub.workbench.chatagent.service.ConversationMemoryService;
import org.javaup.enums.ChatTurnStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTurnContextFactoryTest {

    @Test
    void everyExchangeUsesAnIndependentGraphThreadAndBoundedSeed() {
        ConversationArchiveStore archiveStore = mock(ConversationArchiveStore.class);
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        when(memoryService.loadMemoryContext("conv-1")).thenReturn(ConversationMemoryContext.builder()
            .longTermSummary("长期摘要")
            .build());
        when(archiveStore.listRecentExchanges("conv-1", 5)).thenReturn(List.of(
            exchange(1), exchange(2), exchange(3), exchange(4), exchange(5)
        ));
        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setAgentRecentTurns(4);
        properties.setAgentSeedMaxCharacters(500);
        AgentTurnContextFactory factory = new AgentTurnContextFactory(archiveStore, memoryService, properties);

        AgentTurnContext first = factory.create("conv-1", 11L, RunnableConfig.builder().threadId("conv-1").build());
        AgentTurnContext second = factory.create("conv-1", 12L, RunnableConfig.builder().threadId("conv-1").build());

        assertThat(first.threadId()).isEqualTo("conv-1:exchange:11");
        assertThat(second.threadId()).isEqualTo("conv-1:exchange:12");
        assertThat(first.seedMessages()).hasSizeLessThanOrEqualTo(9);
        assertThat(first.seedMessages()).noneMatch(message -> message.contains("tool-result"));
        assertThat(first.runnableConfig().threadId()).contains("conv-1:exchange:11");
    }

    private ConversationExchangeView exchange(long id) {
        ConversationExchangeView view = new ConversationExchangeView();
        view.setExchangeId(id);
        view.setQuestion("question-" + id);
        view.setAnswer(id == 1 ? "tool-result should not be special history" : "answer-" + id);
        view.setStatus(ChatTurnStatus.COMPLETED);
        return view;
    }
}
