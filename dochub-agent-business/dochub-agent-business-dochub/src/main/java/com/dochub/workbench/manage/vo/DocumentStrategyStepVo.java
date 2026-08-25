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
public class DocumentStrategyStepVo {

    private Integer stepNo;

    private String pipelineType;

    private String pipelineTypeName;

    private Integer strategyType;

    private String strategyName;

    private Integer strategyRole;

    private String strategyRoleName;

    private Integer sourceType;

    private String sourceTypeName;

    private Integer executeStatus;

    private String executeStatusName;

    private String recommendReason;
}
