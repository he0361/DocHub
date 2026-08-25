package com.dochub.workbench.chatagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResetVo {

    private String conversationId;

    private boolean stoppedRunningTask;

    private int removedDialogueCount;

    private int removedExchangeCount;

    private int removedCheckpointCount;

    private String message;
}
