package com.dochub.workbench.manage.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
public class DocumentStrategyConfirmDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    @NotNull(message = "基础方案id不能为空")
    private Long basePlanId;

    private String adjustNote;

    private Long operatorId;

    @Valid
    @NotEmpty(message = "父块流水线不能为空")
    private List<DocumentStrategyStepItemDto> parentSteps;

    @Valid
    @NotEmpty(message = "子块流水线不能为空")
    private List<DocumentStrategyStepItemDto> childSteps;
}
