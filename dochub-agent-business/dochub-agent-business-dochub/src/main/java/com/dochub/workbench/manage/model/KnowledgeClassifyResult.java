package com.dochub.workbench.manage.model;

/**
 * 文枢 DocHub 文档知识域/主题归类结果。
 *
 * <p>由 {@code KnowledgeScopeClassifyService} 调用 LLM 生成：判定文档归属哪个知识域（scope）、
 * 哪个主题（topic）；若无合适归属则生成新的 scope/topic 并持久化，实现知识结构自生长。</p>
 *
 * @param scopeCode        知识域 code（可能是现有 code 或新生成 code）
 * @param scopeName        知识域名称
 * @param scopeNew         是否为本次新生成的知识域
 * @param topicCode        主题 code（无主题时为空）
 * @param topicName        主题名称
 * @param topicNew         是否为本次新生成的主题
 * @param businessCategory 业务类别
 * @param reason           归类理由
 */
public record KnowledgeClassifyResult(
        String scopeCode,
        String scopeName,
        boolean scopeNew,
        String topicCode,
        String topicName,
        boolean topicNew,
        String businessCategory,
        String reason) {
}
