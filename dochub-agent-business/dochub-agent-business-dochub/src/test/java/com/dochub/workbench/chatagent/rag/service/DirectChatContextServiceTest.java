package com.dochub.workbench.chatagent.rag.service;

import com.dochub.workbench.chatagent.model.ConversationExchangeView;
import com.dochub.workbench.chatagent.model.memory.ConversationMemoryContext;
import com.dochub.workbench.chatagent.rag.model.DirectChatContext;
import com.dochub.workbench.chatagent.service.ConversationArchiveStore;
import com.dochub.workbench.chatagent.service.ConversationMemoryService;
import org.javaup.enums.ChatTurnStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirectChatContextServiceTest {

    @Test
    void keepsNewestCompletedTurnsAndTrimsOldestWithinBudget() {
        ConversationArchiveStore archiveStore = mock(ConversationArchiveStore.class);
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        when(memoryService.loadMemoryContext("conv-1")).thenReturn(ConversationMemoryContext.builder()
            .longTermSummary("这是已持久化的会话摘要")
            .build());
        when(archiveStore.listRecentExchanges("conv-1", 3)).thenReturn(List.of(
            exchange(1, "old-question-xxxxxxxx", "old-answer-xxxxxxxx", ChatTurnStatus.COMPLETED),
            exchange(2, "new-question", "new-answer", ChatTurnStatus.COMPLETED),
            exchange(3, "running", "", ChatTurnStatus.RUNNING)
        ));

        DirectChatContextService service = new DirectChatContextService(archiveStore, memoryService);
        DirectChatContext context = service.build("conv-1", 2, 55);

        assertThat(context.promptCharacters()).isLessThanOrEqualTo(55);
        assertThat(context.recentExchanges()).extracting(ConversationExchangeView::getExchangeId)
            .containsExactly(2L);
        assertThat(context.summary()).isNotBlank();
    }

    @Test
    void missingSummaryAndHistoryProduceAnEmptyBoundedContext() {
        ConversationArchiveStore archiveStore = mock(ConversationArchiveStore.class);
        ConversationMemoryService memoryService = mock(ConversationMemoryService.class);
        when(memoryService.loadMemoryContext("empty")).thenReturn(null);
        when(archiveStore.listRecentExchanges("empty", 4)).thenReturn(new ArrayList<>());

        DirectChatContext context = new DirectChatContextService(archiveStore, memoryService)
            .build("empty", 3, 8_000);

        assertThat(context.summary()).isEmpty();
        assertThat(context.recentExchanges()).isEmpty();
        assertThat(context.promptCharacters()).isZero();
    }

    private ConversationExchangeView exchange(long id, String question, String answer, ChatTurnStatus status) {
        ConversationExchangeView view = new ConversationExchangeView();
        view.setExchangeId(id);
        view.setQuestion(question);
        view.setAnswer(answer);
        view.setStatus(status);
        return view;
    }
}
