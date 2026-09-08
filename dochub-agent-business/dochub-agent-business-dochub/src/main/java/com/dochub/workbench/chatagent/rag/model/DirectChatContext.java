package com.dochub.workbench.chatagent.rag.model;

import com.dochub.workbench.chatagent.model.ConversationExchangeView;

import java.util.List;

public record DirectChatContext(String summary, List<ConversationExchangeView> recentExchanges, int promptCharacters) {
}
