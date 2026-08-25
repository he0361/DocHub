package com.dochub.workbench.manage.model.route;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 模型对象
 * @author: zhangjihe
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRouteCandidate {

    private String documentId;

    private String documentName;

    private String lastIndexTaskId;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String businessCategory;

    private String documentTags;

    private BigDecimal score;

    private String reason;
}
