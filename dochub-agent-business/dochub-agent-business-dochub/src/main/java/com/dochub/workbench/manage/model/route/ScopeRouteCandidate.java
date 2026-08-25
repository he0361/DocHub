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
public class ScopeRouteCandidate {

    private String scopeCode;

    private String scopeName;

    private BigDecimal score;

    private String reason;
}
