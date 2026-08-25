package com.dochub.workbench.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 技能场景匹配结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMatchResult {

    private SkillDefinition skill;

    private double score;

    private String reason;
}
