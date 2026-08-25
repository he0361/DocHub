package com.dochub.workbench.chatagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.dochub.workbench.chatagent.model.memory.ConversationSummaryPayload;

import java.time.Instant;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMemorySummaryView {

    private String conversationId;

    private boolean compressionApplied;

    private long coveredExchangeId;

    private int coveredExchangeCount;

    private int compressionCount;

    private int summaryVersion;

    private String summaryText;

    private ConversationSummaryPayload summaryPayload;

    private Instant lastSourceEditTime;

    private Instant updatedAt;
}
