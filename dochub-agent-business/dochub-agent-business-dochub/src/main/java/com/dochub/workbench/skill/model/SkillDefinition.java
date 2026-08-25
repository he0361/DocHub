package com.dochub.workbench.skill.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文枢 DocHub 技能定义（内存注册表中的技能快照）。
 *
 * <p>由 SKILL.md 的 YAML front-matter + 正文解析而来，或从 DB 重建。
 * 技能以"数据资产"形式存在，不注册为 Spring Bean。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDefinition {

    private Long id;

    /** 技能目录名 / 唯一键 */
    private String name;

    private String displayName;

    private String version;

    private String description;

    private String whenToUse;

    /** SKILL.md 正文指令 */
    private String instructions;

    private String category;

    private String tags;

    /** BUILT_IN / MARKET / UPLOAD */
    private String skillType;

    /** classpath / minio */
    private String sourceType;

    private String objectPrefix;

    /** 1:启用 2:停用 3:待审核 */
    private Integer runState;

    private boolean scriptExecEnabled;
}
