package com.dochub.workbench.skill.dto;

import lombok.Data;

/**
 * 文枢 DocHub 技能按名称操作 DTO（安装/启停/删除/详情）。
 */
@Data
public class SkillIdDto {

    private String skillName;
}
