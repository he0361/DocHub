package com.dochub.workbench.modelconfig.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

/**
 * Versioned runtime chat or embedding model configuration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_ai_model_config")
@EqualsAndHashCode(callSuper = true)
public class DochubAiModelConfig extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String modelType;
    private String deploymentType;
    private String compatibilityPreset;
    private String baseUrl;
    private String requestPath;
    private String modelName;
    private String encryptedApiKey;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutMillis;
    private Integer toolCallingSupported;
    private String optionsJson;
    private Long configVersion;
    private Integer active;

    @TableField(value = "active_model_type", insertStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER,
        updateStrategy = com.baomidou.mybatisplus.annotation.FieldStrategy.NEVER)
    private String activeModelType;

    private Long updatedBy;
}
