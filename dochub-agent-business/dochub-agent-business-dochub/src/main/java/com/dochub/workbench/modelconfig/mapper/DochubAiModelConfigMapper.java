package com.dochub.workbench.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import org.apache.ibatis.annotations.Mapper;

/** Mapper for versioned runtime model configurations. */
@Mapper
public interface DochubAiModelConfigMapper extends BaseMapper<DochubAiModelConfig> {
}
