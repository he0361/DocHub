package com.dochub.workbench.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for immutable runtime model configuration audit records. */
@Mapper
public interface DochubAiModelConfigAuditMapper extends BaseMapper<DochubAiModelConfigAudit> {
}
