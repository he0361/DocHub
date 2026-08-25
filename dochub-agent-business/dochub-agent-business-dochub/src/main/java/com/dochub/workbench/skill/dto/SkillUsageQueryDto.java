package com.dochub.workbench.skill.dto;

import lombok.Data;

/**
 * 文枢 DocHub 技能调用记录查询 DTO。
 */
@Data
public class SkillUsageQueryDto {

    private Integer pageNo;

    private Integer pageSize;

    private String skillName;
}
