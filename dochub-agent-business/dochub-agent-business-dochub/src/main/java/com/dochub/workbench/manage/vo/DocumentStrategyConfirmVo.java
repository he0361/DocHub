package com.dochub.workbench.manage.vo;

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
public class DocumentStrategyConfirmVo {

    private Long documentId;

    private Long planId;

    private Integer planVersion;

    private Integer strategyStatus;

    private String strategyStatusName;

    private Boolean normalized;

    private DocumentStrategyPipelineVo parentPipeline;

    private DocumentStrategyPipelineVo childPipeline;
}
