package com.dochub.workbench.chatagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
public class ConversationExchangeDetailQueryDto {

    @NotBlank(message = "conversationId 不能为空")
    private String conversationId;

    @NotBlank(message = "exchangeId 不能为空")
    private String exchangeId;
}
