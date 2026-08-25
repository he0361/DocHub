package com.dochub.workbench.docgen.service;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochub.workbench.chatagent.service.ObservedChatModelService;
import com.dochub.workbench.docgen.config.DocGenProperties;
import com.dochub.workbench.docgen.data.DocTemplateEntity;
import com.dochub.workbench.docgen.support.TemplateVariableResolver;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文枢 DocHub 文档大纲规划器。
 *
 * <p>调用 LLM 根据模板大纲提示词 + 用户需求产出结构化大纲（JSON 数组）。
 * 采用异步 + 超时 + 兜底范式：LLM 失败或超时，回退到模板自带大纲，保证生成链路不中断。</p>
 */
@Slf4j
@Service
public class DocumentOutlinePlanner {

    private final DocGenProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final ObservedChatModelService observedChatModelService;
    private final PromptTemplateService promptTemplateService;
    private final TemplateVariableResolver templateVariableResolver;

    public DocumentOutlinePlanner(DocGenProperties properties,
                                  ObjectMapper objectMapper,
                                  @Qualifier("docGenExecutorService") ExecutorService executorService,
                                  ObservedChatModelService observedChatModelService,
                                  PromptTemplateService promptTemplateService,
                                  TemplateVariableResolver templateVariableResolver) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.executorService = executorService;
        this.observedChatModelService = observedChatModelService;
        this.promptTemplateService = promptTemplateService;
        this.templateVariableResolver = templateVariableResolver;
    }

    /**
     * 规划大纲。失败/超时返回空列表，由调用方回退模板自带大纲。
     */
    public List<String> planOutline(DocTemplateEntity template, String requirement, Map<String, String> variables) {
        if (template == null || StrUtil.isBlank(template.getOutlinePrompt())) {
            return List.of();
        }
        String outlinePrompt = templateVariableResolver.resolve(template.getOutlinePrompt(), variables);
        String userPrompt = promptTemplateService.render(PromptTemplateNames.DOC_OUTLINE, Map.of(
            "outlinePrompt", StrUtil.blankToDefault(outlinePrompt, ""),
            "requirement", StrUtil.blankToDefault(requirement, "")));
        try {
            return CompletableFuture.supplyAsync(() -> planOutlineInternal(userPrompt), executorService)
                .orTimeout(Math.max(properties.getOutlineTimeoutMs(), 1L), TimeUnit.MILLISECONDS)
                .exceptionally(exception -> {
                    log.warn("大纲规划失败，回退模板自带大纲: {}", exception.getMessage());
                    return List.of();
                })
                .join();
        }
        catch (Exception exception) {
            log.warn("大纲规划失败，回退模板自带大纲", exception);
            return List.of();
        }
    }

    private List<String> planOutlineInternal(String userPrompt) {
        String content = observedChatModelService.callText("doc-outline", null, userPrompt, null, null);
        if (StrUtil.isBlank(content)) {
            return List.of();
        }
        String jsonArray = extractJsonArray(content);
        if (jsonArray == null) {
            log.warn("大纲输出不是有效 JSON 数组: {}", content);
            return List.of();
        }
        try {
            List<String> rawList = objectMapper.readValue(jsonArray, new TypeReference<List<String>>() {
            });
            List<String> clean = new ArrayList<>();
            for (String item : rawList) {
                if (StrUtil.isNotBlank(item)) {
                    clean.add(item.trim());
                }
                if (clean.size() >= Math.max(1, properties.getMaxOutlineItems())) {
                    break;
                }
            }
            return clean;
        }
        catch (Exception exception) {
            log.warn("大纲 JSON 解析失败，回退模板自带大纲: {}", exception.getMessage());
            return List.of();
        }
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
    }
}
