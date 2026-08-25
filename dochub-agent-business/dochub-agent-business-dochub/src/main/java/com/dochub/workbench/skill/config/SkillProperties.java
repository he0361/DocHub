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

    /** 是否启用 LLM 精确选技能（优先）；关闭或 LLM 失败时回退标签关键词+bigram 打分 */
    private boolean llmRouterEnabled = true;
}
