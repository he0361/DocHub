package com.dochub.workbench.chatagent.support;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 支撑组件
 * @author: zhangjihe
 **/

public record StreamEventMetadata(
    String conversationId,
    Long exchangeId
) {
}
