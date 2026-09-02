package com.dochub.workbench.chatagent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.chatagent.model.ConversationExchangeView;
import com.dochub.workbench.chatagent.model.memory.ConversationMemoryContext;
import com.dochub.workbench.chatagent.rag.model.DirectChatContext;
import com.dochub.workbench.chatagent.service.ConversationArchiveStore;
import com.dochub.workbench.chatagent.service.ConversationMemoryService;
import org.javaup.enums.ChatTurnStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DirectChatContextService {

    private final ConversationArchiveStore archiveStore;
    private final ConversationMemoryService memoryService;

    public DirectChatContextService(ConversationArchiveStore archiveStore, ConversationMemoryService memoryService) {
        this.archiveStore = archiveStore;
        this.memoryService = memoryService;
    }

    public DirectChatContext build(String conversationId, int recentTurns, int maxCharacters) {
        int turns = Math.max(0, recentTurns);
        int budget = Math.max(0, maxCharacters);
        ConversationMemoryContext memory = memoryService.loadMemoryContext(conversationId);
        String summary = memory == null ? "" : StrUtil.blankToDefault(memory.getLongTermSummary(), "").trim();
        List<ConversationExchangeView> loaded = archiveStore.listRecentExchanges(conversationId, turns + 1);
        List<ConversationExchangeView> completed = loaded == null ? new ArrayList<>() : loaded.stream()
            .filter(exchange -> exchange != null && exchange.getStatus() == ChatTurnStatus.COMPLETED)
            .toList();
        if (completed.size() > turns) {
            completed = completed.subList(completed.size() - turns, completed.size());
        }
        List<ConversationExchangeView> kept = new ArrayList<>(completed);
        while (!kept.isEmpty() && size(summary, kept) > budget) {
            kept.remove(0);
        }
        int remainingForSummary = Math.max(0, budget - size("", kept));
        if (summary.length() > remainingForSummary) {
            summary = summary.substring(Math.max(0, summary.length() - remainingForSummary));
        }
        return new DirectChatContext(summary, List.copyOf(kept), size(summary, kept));
    }

    public String render(DirectChatContext context, String question, int maxCharacters) {
        StringBuilder prompt = new StringBuilder();
        if (context != null && StrUtil.isNotBlank(context.summary())) {
            prompt.append("【会话摘要】\n").append(context.summary()).append("\n\n");
        }
        if (context != null) {
            for (ConversationExchangeView exchange : context.recentExchanges()) {
                prompt.append("用户：").append(StrUtil.blankToDefault(exchange.getQuestion(), "")).append('\n')
                    .append("助手：").append(StrUtil.blankToDefault(exchange.getAnswer(), "")).append("\n\n");
            }
        }
        prompt.append("【当前问题】\n").append(StrUtil.blankToDefault(question, ""));
        String rendered = prompt.toString();
        int budget = Math.max(1, maxCharacters);
        return rendered.length() <= budget ? rendered : rendered.substring(rendered.length() - budget);
    }

    private int size(String summary, List<ConversationExchangeView> exchanges) {
        int size = StrUtil.length(summary);
        for (ConversationExchangeView exchange : exchanges) {
            size += StrUtil.length(exchange.getQuestion()) + StrUtil.length(exchange.getAnswer());
        }
        return size;
    }
}
