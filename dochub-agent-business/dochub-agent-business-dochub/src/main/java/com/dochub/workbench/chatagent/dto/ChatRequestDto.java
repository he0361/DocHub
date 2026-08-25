package com.dochub.workbench.chatagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDto {

    @NotBlank(message = "question 不能为空")
    private String question;
    private String conversationId;

    @NotBlank(message = "chatMode 不能为空")
    private String chatMode;

    private String selectedDocumentId;

    /**
     * 开放式提问的回答方式：REACT_AGENT（ReAct 自主执行）或 PLAN_AND_EXECUTE（计划-执行）。
     * 仅 chatMode=OPEN_CHAT 时生效；为空时默认 REACT_AGENT。
     */
    private String openChatMode;

    /**
     * 通过 /skills 命令强制指定的技能名；为空则按问题自动匹配技能。
     */
    private String forcedSkillName;
}
