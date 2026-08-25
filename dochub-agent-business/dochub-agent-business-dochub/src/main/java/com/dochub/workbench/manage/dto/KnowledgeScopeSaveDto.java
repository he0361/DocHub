package com.dochub.workbench.manage.dto;

import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/
@Data
public class KnowledgeScopeSaveDto {

    private String id;

    private String scopeCode;

    private String scopeName;

    private String parentScopeCode;

    private String description;

    private String aliases;

    private String examples;

    private String sortOrder;

    private String operatorId;
}
