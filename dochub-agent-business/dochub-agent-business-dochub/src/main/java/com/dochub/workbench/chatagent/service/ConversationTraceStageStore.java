package com.dochub.workbench.chatagent.service;

import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageCode;
import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageState;
import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageView;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface ConversationTraceStageStore {

    long startStage(String conversationId,
                    long exchangeId,
                    String traceId,
                    ConversationTraceStageCode stageCode,
                    int stageLevel,
                    Long parentStageId,
                    String executionMode,
                    String summaryText,
                    Object snapshot);

    void finishStage(long stageId,
                     ConversationTraceStageState stageState,
                     String summaryText,
                     String errorMessage,
                     Object snapshot,
                     long durationMs);

    List<ConversationTraceStageView> listStageViews(String conversationId, long exchangeId);

    void deleteStages(String conversationId);
}
