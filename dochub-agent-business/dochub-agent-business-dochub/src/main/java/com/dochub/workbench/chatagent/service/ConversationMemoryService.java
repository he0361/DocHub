package com.dochub.workbench.chatagent.service;

import com.dochub.workbench.chatagent.model.ConversationMemorySummaryView;
import com.dochub.workbench.chatagent.model.memory.ConversationMemoryContext;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface ConversationMemoryService {

    ConversationMemoryContext loadMemoryContext(String conversationId);

    default ConversationMemoryContext loadMemoryContext(String conversationId, ConversationTraceRecorder traceRecorder) {
        return loadMemoryContext(conversationId);
    }

    /** 带当前问题的记忆加载：用于按问题在长期向量记忆中检索相似历史 */
    default ConversationMemoryContext loadMemoryContext(String conversationId, String question, ConversationTraceRecorder traceRecorder) {
        return loadMemoryContext(conversationId, traceRecorder);
    }

    void refreshConversationSummaryAsync(String conversationId);

    ConversationMemorySummaryView getConversationSummary(String conversationId);

    ConversationMemorySummaryView rebuildConversationSummary(String conversationId);

    void deleteConversationSummary(String conversationId);
}
