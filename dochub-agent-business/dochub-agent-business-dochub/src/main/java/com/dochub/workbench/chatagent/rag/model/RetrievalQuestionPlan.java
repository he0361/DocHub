package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 检索阶段真正执行的问题计划
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RetrievalQuestionPlan {

    private String retrievalQuestion;

    private List<String> subQuestions;
}
