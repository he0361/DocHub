package com.dochub.workbench.manage.dto;

import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/
@Data
public class KnowledgeTopicSaveDto {

    private String id;

    private String topicCode;

    private String topicName;

    private String scopeCode;

    private String description;

    private String aliases;

    private String examples;

    private String answerShape;

    private String executionPreference;

    private String sortOrder;

    private String operatorId;
}
