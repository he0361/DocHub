package com.dochub.workbench.docgen.support;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文枢 DocHub 模板变量解析器。
 *
 * <p>负责把模板正文/大纲提示词中的 {{变量名}} 占位符替换为运行时变量值。
 * 刻意使用 {{ }} 定界符，与 PromptTemplateService 的 &lt; &gt; 定界符错开，互不干扰。</p>
 */
@Component
public class TemplateVariableResolver {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    /**
     * 将模板文本中的 {{变量}} 全部替换为变量值；未提供的变量保留原样。
     */
    public String resolve(String templateText, Map<String, String> variables) {
        if (StrUtil.isBlank(templateText)) {
            return templateText;
        }
        if (variables == null || variables.isEmpty()) {
            return templateText;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(templateText);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = variables.get(name);
            String replacement = value == null ? matcher.group(0) : value;
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 收集模板文本中出现过的所有 {{变量名}}，用于前端表单驱动或校验。
     */
    public java.util.Set<String> collectVariables(String templateText) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        if (StrUtil.isBlank(templateText)) {
            return names;
        }
        Matcher matcher = VARIABLE_PATTERN.matcher(templateText);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
