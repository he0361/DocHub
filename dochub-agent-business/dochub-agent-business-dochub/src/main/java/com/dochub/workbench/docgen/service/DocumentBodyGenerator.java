package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.docgen.config.DocGenProperties;
import com.dochub.workbench.docgen.data.DocTemplateEntity;
import com.dochub.workbench.docgen.support.TemplateVariableResolver;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文枢 DocHub 文档正文生成器。
 *
 * <p>以模板骨架（变量已替换）+ 大纲 + 变量信息 + 用户需求为输入，
 * 让 LLM 直接产出 Markdown 正文。</p>
 */
@Slf4j
@Service
public class DocumentBodyGenerator {

    private final DocGenProperties properties;
    private final ExecutorService executorService;
    private final ObservedChatModelService observedChatModelService;
    private final PromptTemplateService promptTemplateService;
    private final TemplateVariableResolver templateVariableResolver;

    public DocumentBodyGenerator(DocGenProperties properties,
                                 @Qualifier("docGenExecutorService") ExecutorService executorService,
                                 ObservedChatModelService observedChatModelService,
                                 PromptTemplateService promptTemplateService,
                                 TemplateVariableResolver templateVariableResolver) {
        this.properties = properties;
        this.executorService = executorService;
        this.observedChatModelService = observedChatModelService;
        this.promptTemplateService = promptTemplateService;
        this.templateVariableResolver = templateVariableResolver;
    }

    /**
     * 生成 Markdown 正文。超时/失败返回 null，由调用方标记失败记录。
     */
    public String generateBody(DocTemplateEntity template, String requirement,
                               Map<String, String> variables, List<String> outline) {
        String templateContent = templateVariableResolver.resolve(template.getContentTemplateText(), variables);
        String systemPrompt = promptTemplateService.render(PromptTemplateNames.DOC_BODY_SYSTEM, Map.of());
        String userPrompt = promptTemplateService.render(PromptTemplateNames.DOC_BODY_USER, Map.of(
            "templateContent", StrUtil.blankToDefault(templateContent, ""),
            "outline", StrUtil.blankToDefault(outline == null ? "" : String.join("\n", outline), ""),
            "variablesSummary", formatVariables(variables),
            "requirement", StrUtil.blankToDefault(requirement, "")));
        try {
            return CompletableFuture.supplyAsync(
                    () -> observedChatModelService.callText("doc-body", systemPrompt, userPrompt, null, null),
                    executorService)
                .orTimeout(Math.max(properties.getBodyTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    log.warn("正文生成失败: {}", exception.getMessage());
                    return null;
                })
                .join();
        }
        catch (Exception exception) {
            log.warn("正文生成失败", exception);
            return null;
        }
    }

    private String formatVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return "（无）";
        }
        StringJoiner joiner = new StringJoiner("\n");
        variables.forEach((key, value) -> {
            if (StrUtil.isNotBlank(value)) {
                joiner.add(key + "：" + value);
            }
        });
        return joiner.length() == 0 ? "（无）" : joiner.toString();
    }
}
