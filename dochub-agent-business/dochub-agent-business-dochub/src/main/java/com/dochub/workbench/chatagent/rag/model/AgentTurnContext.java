package com.dochub.workbench.chatagent.rag.model;

import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.List;

public record AgentTurnContext(String threadId, RunnableConfig runnableConfig, List<String> seedMessages) {

    public String prependTo(String currentPrompt) {
        if (seedMessages == null || seedMessages.isEmpty()) {
            return currentPrompt;
        }
        return String.join("\n", seedMessages) + "\n\n【当前任务】\n" + currentPrompt;
    }
}
