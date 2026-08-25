package com.dochub.workbench.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 技能市场列表项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillMarketItemVo {

    private String skillName;

    private String displayName;

    private String version;

    private String description;

    private String category;

    private String tags;

    private String skillType;

    private Integer runState;

    private boolean installed;

    private Integer installCount;
}
