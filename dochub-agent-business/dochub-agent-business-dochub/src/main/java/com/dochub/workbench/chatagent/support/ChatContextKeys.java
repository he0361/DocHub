package com.dochub.workbench.chatagent.support;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 支撑组件
 * @author: zhangjihe
 **/
public final class ChatContextKeys {

    public static final String EVENT_SINK = "chat.event.sink";
    public static final String EVENT_METADATA = "chat.event.metadata";
    public static final String DEBUG_TRACE = "chat.debug.trace";
    public static final String TRACE_ID = "chat.trace.id";
    public static final String REFERENCES = "chat.references";
    public static final String USED_TOOLS = "chat.used.tools";
    public static final String THINKING_STEPS = "chat.thinking.steps";
    public static final String QUESTION = "chat.question";
    public static final String CHAT_MODE = "chat.mode";
    /** 开放式提问的回答方式：REACT_AGENT / PLAN_AND_EXECUTE（用户可选） */
    public static final String OPEN_CHAT_MODE = "chat.open.chat.mode";
    /** 用户通过 /skills 命令强制指定的技能名（为空则走自动匹配） */
    public static final String FORCED_SKILL_NAME = "chat.forced.skill.name";
    public static final String CURRENT_DATE = "chat.current.date";
    public static final String CURRENT_DATE_TEXT = "chat.current.date.text";
    public static final String SELECTED_DOCUMENT_ID = "chat.selected.document.id";
    public static final String SELECTED_DOCUMENT_NAME = "chat.selected.document.name";
    public static final String SELECTED_TASK_ID = "chat.selected.task.id";

    private ChatContextKeys() {
    }
}
