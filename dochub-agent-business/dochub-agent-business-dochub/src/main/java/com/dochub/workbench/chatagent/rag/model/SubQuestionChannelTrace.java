package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 单个子问题在某个检索通道上的执行痕迹
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuestionChannelTrace {

    private String channelName;

    private int recalledCount;

    private int acceptedCount;
}
