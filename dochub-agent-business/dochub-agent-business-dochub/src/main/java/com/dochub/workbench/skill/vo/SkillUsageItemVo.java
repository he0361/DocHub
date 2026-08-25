package com.dochub.workbench.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 文枢 DocHub 技能调用记录项。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillUsageItemVo {

    private String skillName;

    private String conversationId;

    private String scene;

    private java.math.BigDecimal matchedScore;

    private String matchedReason;

    private Date createTime;
}
