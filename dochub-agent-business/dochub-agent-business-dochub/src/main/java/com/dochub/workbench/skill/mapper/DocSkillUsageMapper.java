package com.dochub.workbench.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.skill.data.DocSkillUsageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文枢 DocHub 技能调用记录 Mapper。
 */
@Mapper
public interface DocSkillUsageMapper extends BaseMapper<DocSkillUsageEntity> {
}
