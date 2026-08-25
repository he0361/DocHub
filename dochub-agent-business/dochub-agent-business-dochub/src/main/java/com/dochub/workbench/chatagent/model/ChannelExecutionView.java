package com.dochub.workbench.chatagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChannelExecutionView {

    private Long id;
    private String traceId;
    private int subQuestionIndex;
    private String subQuestion;
    private String channelType;
    private int executionState;
    private Instant startTime;
    private Instant endTime;
    private Long durationMs;
    private int recalledCount;
    private int acceptedCount;
    private int finalSelectedCount;
    private BigDecimal avgScore;
    private BigDecimal maxScore;
    private BigDecimal minScore;
    private String errorMessage;
    private Instant createTime;
}
