package com.dochub.workbench.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
public class DocumentTaskLogQueryDto {

    @NotNull(message = "任务id不能为空")
    private Long taskId;

    private Integer pageNo;

    private Integer pageSize;
}
