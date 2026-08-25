package com.dochub.workbench.chatagent.rag.retrieve.channel;

import com.dochub.workbench.chatagent.rag.model.ConversationExecutionPlan;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 检索通道抽象
 * @author: zhangjihe
 **/

public interface RetrievalChannel {

    String channelName();

    boolean supports(ConversationExecutionPlan plan);

    RetrievalChannelResult retrieve(String subQuestion, ConversationExecutionPlan plan);
}
