package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.route.KnowledgeRouteDecision;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/
public interface KnowledgeRouteService {

    KnowledgeRouteDecision route(String question, String rewriteQuestion);

    void recordShadowRoute(String conversationId,
                           long exchangeId,
                           Long selectedDocumentId,
                           String question,
                           String rewriteQuestion);

    void recordAutoRoute(String conversationId,
                         long exchangeId,
                         String question,
                         String rewriteQuestion,
                         KnowledgeRouteDecision decision);
}
