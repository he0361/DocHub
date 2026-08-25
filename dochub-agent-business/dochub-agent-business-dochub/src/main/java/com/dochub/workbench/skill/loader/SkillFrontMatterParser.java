package com.dochub.workbench.skill.loader;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.skill.model.SkillDefinition;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

/**
 * 文枢 DocHub SKILL.md 解析器。
 *
 * <p>兼容 Claude Code 技能目录格式：SKILL.md 以 YAML front-matter 开头
 * （--- 包裹的 name/description/when_to_use 等键），其余为正文指令。</p>
 */
@Component
public class SkillFrontMatterParser {

    private static final String FRONT_MATTER_DELIMITER = "---";

    /**
     * 解析 SKILL.md 内容。
     *
     * @param skillName 目录名（唯一键，front-matter 缺失 name 时兜底）
     * @param content   SKILL.md 全文
     */
    public SkillDefinition parse(String skillName, String content) {
        FrontMatterResult parsed = splitFrontMatter(content);
        Map<String, Object> meta = parseYaml(parsed.frontMatter());

        String name = firstNotBlank(valueOf(meta.get("name")), skillName);
        return SkillDefinition.builder()
            .name(name)
            .displayName(firstNotBlank(valueOf(meta.get("display_name")), valueOf(meta.get("name")), name))
            .version(firstNotBlank(valueOf(meta.get("version")), "1.0.0"))
            .description(valueOf(meta.get("description")))
            .whenToUse(valueOf(meta.get("when_to_use")))
            .category(valueOf(meta.get("category")))
            .tags(valueOf(meta.get("tags")))
            .instructions(parsed.body().trim())
            .scriptExecEnabled(parseBoolean(meta.get("script_exec_enabled"), false))
            .runState(1)
            .build();
    }

    private FrontMatterResult splitFrontMatter(String content) {
        if (StrUtil.isBlank(content)) {
            return new FrontMatterResult("", "");
        }
        String text = content.trim();
        if (!text.startsWith(FRONT_MATTER_DELIMITER)) {
            return new FrontMatterResult("", text);
        }
        int firstEnd = text.indexOf('\n', FRONT_MATTER_DELIMITER.length());
        if (firstEnd < 0) {
            return new FrontMatterResult("", text);
        }
        int secondStart = text.indexOf(FRONT_MATTER_DELIMITER, firstEnd + 1);
        if (secondStart < 0) {
            return new FrontMatterResult("", text);
        }
        String frontMatter = text.substring(FRONT_MATTER_DELIMITER.length(), firstEnd).trim() + "\n"
            + text.substring(firstEnd + 1, secondStart).trim();
        String body = text.substring(secondStart + FRONT_MATTER_DELIMITER.length());
        return new FrontMatterResult(frontMatter, body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml(String frontMatter) {
        if (StrUtil.isBlank(frontMatter)) {
            return Map.of();
        }
        try {
            Object parsed = new Yaml().load(frontMatter);
            return parsed instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        }
        catch (Exception exception) {
            return Map.of();
        }
    }

    private String valueOf(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return String.join(",", list.stream().map(Object::toString).toList());
        }
        return StrUtil.blankToDefault(value.toString().trim(), null);
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return "true".equalsIgnoreCase(value.toString()) || "1".equals(value.toString());
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private record FrontMatterResult(String frontMatter, String body) {
    }
}
