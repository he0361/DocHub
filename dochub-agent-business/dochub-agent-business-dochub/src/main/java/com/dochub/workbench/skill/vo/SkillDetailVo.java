package com.dochub.workbench.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 技能详情。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDetailVo {

    private String skillName;

    private String displayName;

    private String version;

    private String description;

    private String whenToUse;

    private String instructions;

    private String category;

    private String tags;

    private String author;

    private String skillType;

    private String sourceType;

    private String objectPrefix;

    private Integer runState;

    private boolean scriptExecEnabled;
}
