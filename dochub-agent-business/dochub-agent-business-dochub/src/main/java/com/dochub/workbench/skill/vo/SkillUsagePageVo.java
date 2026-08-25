package com.dochub.workbench.skill.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文枢 DocHub 技能调用记录分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillUsagePageVo {

    private Integer pageNo;

    private Integer pageSize;

    private Long total;

    private List<SkillUsageItemVo> records;
}
