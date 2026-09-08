package com.dochub.workbench.chatagent.rag.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.dochub.workbench.chatagent.config.ChatAgentProperties;
import com.dochub.workbench.chatagent.model.ConversationExchangeView;
import com.dochub.workbench.chatagent.model.memory.ConversationMemoryContext;
import com.dochub.workbench.chatagent.rag.model.AgentTurnContext;
import com.dochub.workbench.chatagent.service.ConversationArchiveStore;
import com.dochub.workbench.chatagent.service.ConversationMemoryService;
import org.javaup.enums.ChatTurnStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AgentTurnContextFactory {

    private final ConversationArchiveStore archiveStore;
    private final ConversationMemoryService memoryService;
    private final ChatAgentProperties properties;

    public AgentTurnContextFactory(ConversationArchiveStore archiveStore,
                                   ConversationMemoryService memoryService,
                                   ChatAgentProperties properties) {
        this.archiveStore = archiveStore;
        this.memoryService = memoryService;
        this.properties = properties;
    }

    public AgentTurnContext create(String conversationId, long exchangeId, RunnableConfig parent) {
        return create(conversationId, exchangeId, "", parent);
    }

    public AgentTurnContext create(String conversationId, long exchangeId, String suffix, RunnableConfig parent) {
        String threadId = conversationId + ":exchange:" + exchangeId
            + (StrUtil.isBlank(suffix) ? "" : ":" + suffix.trim());
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        if (parent != null && parent.context() != null) {
            config.context().putAll(parent.context());
        }
        List<String> seed = boundedSeed(conversationId);
        return new AgentTurnContext(threadId, config, seed);
    }

    private List<String> boundedSeed(String conversationId) {
        int turns = Math.max(0, properties.getAgentRecentTurns());
        int budget = Math.max(0, properties.getAgentSeedMaxCharacters());
        ConversationMemoryContext memory = memoryService.loadMemoryContext(conversationId);
        List<String> result = new ArrayList<>();
        String summary = memory == null ? "" : StrUtil.blankToDefault(memory.getLongTermSummary(), "").trim();
        if (StrUtil.isNotBlank(summary)) {
            result.add("系统会话摘要：" + summary);
        }
        List<ConversationExchangeView> loaded = archiveStore.listRecentExchanges(conversationId, turns + 1);
        List<ConversationExchangeView> completed = loaded == null ? List.of() : loaded.stream()
            .filter(exchange -> exchange != null && exchange.getStatus() == ChatTurnStatus.COMPLETED)
            .toList();
        int from = Math.max(0, completed.size() - turns);
        for (ConversationExchangeView exchange : completed.subList(from, completed.size())) {
            result.add("历史用户：" + StrUtil.blankToDefault(exchange.getQuestion(), ""));
            result.add("历史助手：" + StrUtil.blankToDefault(exchange.getAnswer(), ""));
        }
        while (!result.isEmpty() && characters(result) > budget) {
            result.remove(0);
        }
        return List.copyOf(result);
    }

    private int characters(List<String> messages) {
        return messages.stream().mapToInt(String::length).sum();
    }
}
