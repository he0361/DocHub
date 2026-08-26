package com.dochub.workbench.modelconfig.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

/**
 * Immutable audit trail for runtime model configuration operations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_ai_model_config_audit")
@EqualsAndHashCode(callSuper = true)
public class DochubAiModelConfigAudit extends BaseTableData {

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
}
