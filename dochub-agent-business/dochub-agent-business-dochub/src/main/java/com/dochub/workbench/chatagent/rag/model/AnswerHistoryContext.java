package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 回答阶段最终使用的历史上下文
 * @author: zhangjihe
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerHistoryContext {

    private String renderedText;

    private String structuredContext;

    private String recentContext;

    private boolean followUpQuestion;

    private Integer totalBudget;

    private Integer recentBudget;

    private Integer structuredBudget;

    public boolean isEmpty() {
        return renderedText == null || renderedText.isBlank();
    }
}
