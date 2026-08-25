package com.dochub.workbench.chatagent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.javaup.enums.ChatQueryMode;

import java.time.LocalDate;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

@Data
@AllArgsConstructor
public class StreamLaunchPlan {

    private final String question;

    private final String conversationId;

    private final ChatQueryMode chatMode;

    private final Long selectedDocumentId;

    private final String selectedDocumentName;

    private final Long selectedTaskId;

    /** 开放式提问的回答方式：REACT_AGENT / PLAN_AND_EXECUTE */
    private final String openChatMode;

    /** 用户通过 /skills 命令强制指定的技能名（为空则自动匹配） */
    private final String forcedSkillName;

    private final String leaseKey;

    private final String leaseOwnerToken;

    private final LocalDate currentDate;

    private final String currentDateText;
}
