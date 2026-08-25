package com.dochub.workbench.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 技能安装/启停结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillInstallResultVo {

    private String skillName;

    private String displayName;

    private Integer runState;
}
