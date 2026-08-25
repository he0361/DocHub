package com.dochub.workbench.chatagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.dochub.workbench.chatagent.model.trace.ConversationTraceStageView;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExchangeDetailView {

    private String conversationId;

    private ConversationExchangeView exchange;

    private List<ConversationTraceStageView> stageTraces;
}
