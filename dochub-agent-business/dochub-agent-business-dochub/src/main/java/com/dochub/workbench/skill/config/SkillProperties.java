package com.dochub.workbench.skill.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文枢 DocHub 技能子系统配置（app.manage.skill.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.manage.skill")
public class SkillProperties {

    /** 是否启用技能子系统 */
    private boolean enabled = true;

    /** 内置技能扫描目录（classpath） */
    private String builtInDir = "skills";

    /** 场景路由命中阈值（bigram Jaccard 分数） */
    private double matchThreshold = 0.12;

    /** 单个 SKILL.md 内容大小上限（字节） */
    private int maxSkillMdBytes = 64 * 1024;

    /** 是否允许在规则候选难以区分时调用 LLM 精确选技能 */
    private boolean llmRouterEnabled = true;

    /** Rule matches below this score are treated as no match. */
    private double minimumRuleScore = 0.45D;

    /** A top rule score at or above this value may bypass the LLM. */
    private double strongRuleThreshold = 0.82D;

    /** Top candidates closer than this score are considered ambiguous. */
    private double ambiguityGap = 0.10D;
}
