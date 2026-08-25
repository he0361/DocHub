package com.dochub.workbench.manage.dto;

import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/
@Data
public class KnowledgeRouteTraceQueryDto {

    private String conversationId;

    private String mode;

    private String routeStatus;

    private String pageNo;

    private String pageSize;
}
