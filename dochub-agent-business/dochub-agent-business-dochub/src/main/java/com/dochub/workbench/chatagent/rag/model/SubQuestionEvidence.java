package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.dochub.workbench.chatagent.model.SearchReference;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 单个子问题的证据容器
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionEvidence {

    private int subQuestionIndex;

    private String subQuestion;

    private List<Document> documents;

    private List<SearchReference> references;

    private List<SubQuestionChannelTrace> channelTraces;

    private Integer fusedCandidateCount;

    private Integer parentCandidateCount;

    private Integer rerankedCandidateCount;
}
