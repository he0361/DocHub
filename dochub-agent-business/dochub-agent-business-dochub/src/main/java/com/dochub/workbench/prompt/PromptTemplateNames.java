package com.dochub.workbench.prompt;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Prompt 模板名称常量
 * @author: zhangjihe
 **/
public final class PromptTemplateNames {

    public static final String AGENT_QUESTION = "agent-question";
    public static final String CHAT_QUERY_REWRITE = "chat-query-rewrite";
    public static final String CONVERSATION_SUMMARY_MERGE = "conversation-summary-merge";
    public static final String CONVERSATION_SUMMARY_SYSTEM = "conversation-summary-system";
    public static final String DOCUMENT_GRAPH_ONLY_INTENT = "document-graph-only-intent";
    public static final String DOCUMENT_LLM_SPLIT = "document-llm-split";
    public static final String DOCUMENT_STRUCTURE_AMBIGUITY = "document-structure-ambiguity";
    public static final String DOCUMENT_STRUCTURE_AMBIGUITY_CANDIDATE = "document-structure-ambiguity-candidate";
    public static final String RAG_ANSWER_DOCUMENT_REFERENCE = "rag-answer-document-reference";
    public static final String RAG_ANSWER_NO_EVIDENCE = "rag-answer-no-evidence";
    public static final String RAG_ANSWER_OMITTED_EVIDENCE = "rag-answer-omitted-evidence";
    public static final String RAG_ANSWER_REUSE_REFERENCE = "rag-answer-reuse-reference";
    public static final String RAG_ANSWER_SUB_QUESTION_EVIDENCE = "rag-answer-sub-question-evidence";
    public static final String RAG_ANSWER_SYSTEM = "rag-answer-system";
    public static final String RAG_ANSWER_USER = "rag-answer-user";
    public static final String RAG_ANSWER_WEB_REFERENCE = "rag-answer-web-reference";
    public static final String RECOMMENDATION_USER = "recommendation-user";

    /** 文枢 DocHub 工作台：文档大纲规划 */
    public static final String DOC_OUTLINE = "doc-outline";
    /** 文枢 DocHub 工作台：文档正文生成系统提示词 */
    public static final String DOC_BODY_SYSTEM = "doc-body-system";
    /** 文枢 DocHub 工作台：文档正文生成用户提示词 */
    public static final String DOC_BODY_USER = "doc-body-user";
    /** 文枢 DocHub 工作台：参考文档仿写系统提示词 */
    public static final String DOC_REFERENCE_SYSTEM = "doc-reference-system";
    /** 文枢 DocHub 工作台：参考文档仿写用户提示词 */
    public static final String DOC_REFERENCE_USER = "doc-reference-user";
    /** 知识路由：文档知识域/主题归类（支持自生长生成新 scope/topic） */
    public static final String KNOWLEDGE_SCOPE_CLASSIFY = "knowledge-scope-classify";
    /** 计划-执行：生成执行步骤计划 */
    public static final String PLAN_EXECUTE_PLANNER = "plan-execute-planner";
    /** 计划-执行：汇总各步骤结果为最终回答 */
    public static final String PLAN_EXECUTE_FINAL = "plan-execute-final";
    /** 技能路由：LLM 根据提问选择最合适的技能（精确匹配） */
    public static final String SKILL_ROUTER = "skill-router";

    private PromptTemplateNames() {
    }
}
