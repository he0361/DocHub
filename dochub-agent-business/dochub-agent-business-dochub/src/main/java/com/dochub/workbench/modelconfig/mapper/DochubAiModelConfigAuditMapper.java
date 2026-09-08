package com.dochub.workbench.modelconfig.mapper;

import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Insert-only persistence API for immutable runtime model configuration audit records.
 */
@Mapper
public interface DochubAiModelConfigAuditMapper {

    @Insert("""
        INSERT INTO dochub_ai_model_config_audit
            (id, model_config_id, model_type, config_version, action, success, masked_endpoint, operator, error, create_time)
        VALUES
            (#{audit.id}, #{audit.modelConfigId}, #{audit.modelType}, #{audit.configVersion}, #{audit.action},
             #{audit.success}, #{audit.maskedEndpoint}, #{audit.operator}, #{audit.error}, #{audit.createTime})
        """)
    int insert(@Param("audit") DochubAiModelConfigAudit audit);
}
