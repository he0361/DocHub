package com.dochub.workbench.skill.dto;

import lombok.Data;

/**
 * 文枢 DocHub 技能市场查询 DTO。
 */
@Data
public class SkillMarketQueryDto {

    private Integer pageNo;

    private Integer pageSize;

    private String keyword;

    private String category;
}
