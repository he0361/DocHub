package com.dochub.workbench.skill.support;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.model.SkillMatchResult;
import org.springframework.stereotype.Component;

/**
 * 文枢 DocHub 技能指令合成器。
 *
 * <p>阶段二采用"固定工具并集 + 指令注入"方案：命中的技能不新增工具、不执行脚本，
 * 而是把 SKILL.md 的适用场景与执行说明拼进 agentQuestion，由 LLM 遵循技能指令作答。
 * 天然安全、零生命周期改动。</p>
 */
@Component
public class SkillQuestionComposer {

    /**
     * 把命中的技能指令拼接到 agentQuestion 末尾。
     */
    public String compose(String agentQuestion, SkillMatchResult match) {
        if (match == null || match.getSkill() == null) {
            return agentQuestion;
        }
        SkillDefinition skill = match.getSkill();
        StringBuilder builder = new StringBuilder(StrUtil.blankToDefault(agentQuestion, ""));
        builder.append("\n\n[系统已为本次任务启用专业能力：")
            .append(StrUtil.isNotBlank(skill.getDisplayName()) ? skill.getDisplayName() : skill.getName())
            .append("]");
        if (StrUtil.isNotBlank(skill.getWhenToUse())) {
            builder.append("\n适用场景：").append(skill.getWhenToUse());
        }
        if (StrUtil.isNotBlank(skill.getInstructions())) {
            builder.append("\n执行说明：\n").append(skill.getInstructions());
        }
        return builder.toString();
    }
}
