package com.dochub.workbench.modelconfig.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Value;

import java.util.Date;

/**
 * Immutable audit trail for runtime model configuration operations.
 */
@Value
@TableName("dochub_ai_model_config_audit")
public class DochubAiModelConfigAudit {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long modelConfigId;
    private String modelType;
    private Long configVersion;
    private String action;
    private Integer success;
    private String maskedEndpoint;
    private Long operator;
    private String error;
    private Date createTime;
}
