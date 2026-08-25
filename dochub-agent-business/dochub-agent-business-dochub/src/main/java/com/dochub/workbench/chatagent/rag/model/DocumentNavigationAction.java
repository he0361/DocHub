package com.dochub.workbench.chatagent.rag.model;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 文档导航动作
 * @author: zhangjihe
 **/

public enum DocumentNavigationAction {
    TOPIC_CONTINUE,
    TOPIC_SWITCH,
    FRESH_TOPIC,
    SIBLING_SECTION_SWITCH,
    CHILD_SECTION_DESCEND,
    ANCESTOR_SECTION_RETURN,
    ITEM_REFERENCE,
    SECTION_ADJACENCY_LOOKUP
}
