package com.dochub.workbench.skill.registry;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.skill.model.SkillDefinition;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文枢 DocHub 技能注册表。
 *
 * <p>内存持有全部已加载技能（ConcurrentHashMap），热加载 = 更新 map，不触碰 Spring 容器。
 * 下载/启用技能后直接调用 register/unregister 即可生效，无需重启。</p>
 */
@Component
public class SkillRegistry {

    private final ConcurrentHashMap<String, SkillDefinition> registry = new ConcurrentHashMap<>();

    public void register(SkillDefinition skill) {
        if (skill != null && StrUtil.isNotBlank(skill.getName())) {
            registry.put(skill.getName(), skill);
        }
    }

    public void unregister(String name) {
        if (StrUtil.isNotBlank(name)) {
            registry.remove(name);
        }
    }

    public SkillDefinition get(String name) {
        return StrUtil.isBlank(name) ? null : registry.get(name);
    }

    /** 全部已启用技能（runState == 1） */
    public List<SkillDefinition> allEnabled() {
        return registry.values().stream()
            .filter(skill -> skill.getRunState() != null && skill.getRunState() == 1)
            .toList();
    }

    /** 全部已加载技能（含停用/待审核） */
    public List<SkillDefinition> all() {
        return List.copyOf(registry.values());
    }

    public boolean isEnabled(String name) {
        SkillDefinition skill = get(name);
        return skill != null && skill.getRunState() != null && skill.getRunState() == 1;
    }

    public int size() {
        return registry.size();
    }
}
