package com.dochub.workbench.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
public class DocumentStrategyStepItemDto {

    private Integer stepNo;

    @NotNull(message = "策略类型不能为空")
    private Integer strategyType;
}
