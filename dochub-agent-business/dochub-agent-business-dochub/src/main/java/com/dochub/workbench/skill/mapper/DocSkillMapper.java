package com.dochub.workbench.skill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.skill.data.DocSkillEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文枢 DocHub 技能注册表 Mapper。
 */
@Mapper
public interface DocSkillMapper extends BaseMapper<DocSkillEntity> {
}
