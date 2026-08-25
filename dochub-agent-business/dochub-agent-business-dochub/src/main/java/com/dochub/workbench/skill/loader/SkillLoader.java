package com.dochub.workbench.skill.loader;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.skill.config.SkillProperties;
import com.dochub.workbench.skill.data.DocSkillEntity;
import com.dochub.workbench.skill.mapper.DocSkillMapper;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.registry.SkillRegistry;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文枢 DocHub 技能加载器。
 *
 * <p>启动时加载两路技能：
 * 1. classpath 内置技能（skills 目录下各技能包的 SKILL.md）；
 * 2. DB 中已启用（run_state=1）的技能。
 * DB 记录后注册，同名时以 DB 的启停状态为准。</p>
 */
@Slf4j
@Component
public class SkillLoader {

    private final SkillProperties properties;
    private final SkillFrontMatterParser parser;
    private final SkillRegistry registry;
    private final DocSkillMapper docSkillMapper;

    public SkillLoader(SkillProperties properties,
                       SkillFrontMatterParser parser,
                       SkillRegistry registry,
                       DocSkillMapper docSkillMapper) {
        this.properties = properties;
        this.parser = parser;
        this.registry = registry;
        this.docSkillMapper = docSkillMapper;
    }

    @PostConstruct
    public void loadSkills() {
        if (!properties.isEnabled()) {
            log.info("技能子系统未启用，跳过技能加载。");
            return;
        }
        loadBuiltInSkills();
        loadDatabaseSkills();
        log.info("技能加载完成，共注册 {} 个技能。", registry.size());
    }

    private void loadBuiltInSkills() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:" + properties.getBuiltInDir() + "/*/SKILL.md");
            for (Resource resource : resources) {
                try {
                    String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
                    String skillName = deriveName(resource);
                    SkillDefinition definition = parser.parse(skillName, content);
                    if (StrUtil.isBlank(definition.getName())) {
                        continue;
                    }
                    definition.setSkillType("BUILT_IN");
                    definition.setSourceType("classpath");
                    definition.setObjectPrefix(properties.getBuiltInDir() + "/" + definition.getName() + "/");
                    // 只有"已安装且启用"的内置技能才激活触发；未安装/已停用的一律按未启用加载（runState=0）。
                    // 内置技能仍注册进注册表用于市场展示，但 runState!=1 不会进入 allEnabled()，故不会自动触发。
                    DocSkillEntity installed = docSkillMapper.selectOne(new LambdaQueryWrapper<DocSkillEntity>()
                        .eq(DocSkillEntity::getSkillName, definition.getName())
                        .eq(DocSkillEntity::getStatus, BusinessStatus.YES.getCode())
                        .last("LIMIT 1"));
                    if (installed != null) {
                        definition.setId(installed.getId());
                        definition.setRunState(installed.getRunState() != null && installed.getRunState() == 1 ? 1 : 0);
                        log.info("加载内置技能（已安装，runState={}）: {}", definition.getRunState(), definition.getName());
                    }
                    else {
                        definition.setRunState(0);
                        log.info("加载内置技能（未安装，需先安装启用）: {}", definition.getName());
                    }
                    registry.register(definition);
                }
                catch (IOException exception) {
                    log.warn("读取内置技能失败: {}", resource, exception);
                }
            }
        }
        catch (IOException exception) {
            log.warn("扫描内置技能目录失败", exception);
        }
    }

    private void loadDatabaseSkills() {
        List<DocSkillEntity> entities = docSkillMapper.selectList(new LambdaQueryWrapper<DocSkillEntity>()
            .eq(DocSkillEntity::getStatus, BusinessStatus.YES.getCode())
            .eq(DocSkillEntity::getRunState, 1));
        for (DocSkillEntity entity : entities) {
            SkillDefinition definition = toDefinition(entity);
            registry.register(definition);
            log.info("加载数据库技能: {}（状态 {}）", definition.getName(), definition.getRunState());
        }
    }

    private SkillDefinition toDefinition(DocSkillEntity entity) {
        return SkillDefinition.builder()
            .id(entity.getId())
            .name(entity.getSkillName())
            .displayName(entity.getDisplayName())
            .version(entity.getVersion())
            .description(entity.getDescription())
            .whenToUse(entity.getWhenToUse())
            .instructions(entity.getInstructions())
            .category(entity.getCategory())
            .tags(entity.getTags())
            .skillType(entity.getSkillType())
            .sourceType(entity.getSourceType())
            .objectPrefix(entity.getObjectPrefix())
            .runState(entity.getRunState())
            .scriptExecEnabled(entity.getScriptExecEnabled() != null && entity.getScriptExecEnabled() == 1)
            .build();
    }

    private String deriveName(Resource resource) {
        try {
            String url = resource.getURL().toString().replace('\\', '/');
            int slash = url.lastIndexOf('/');
            String parent = slash >= 0 ? url.substring(0, slash) : url;
            int parentSlash = parent.lastIndexOf('/');
            return parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
        }
        catch (IOException exception) {
            return null;
        }
    }
}
