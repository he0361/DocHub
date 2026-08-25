package com.dochub.workbench.skill.router;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.dochub.workbench.skill.config.SkillProperties;
import com.dochub.workbench.skill.data.DocSkillUsageEntity;
import com.dochub.workbench.skill.mapper.DocSkillUsageMapper;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.model.SkillMatchResult;
import com.dochub.workbench.skill.registry.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文枢 DocHub 技能场景路由器。
 *
 * <p>优先用 LLM 根据「提问 + 各已启用技能描述」精确选技能（生产级）；
 * LLM 不可用/关闭时回退到标签关键词 + bigram 打分。命中的技能挂到执行计划上。</p>
 */
@Slf4j
@Component
public class SkillSceneRouter {

    private final SkillProperties properties;
    private final SkillRegistry registry;
    private final DocSkillUsageMapper usageMapper;
    private final UidGenerator uidGenerator;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final PromptTemplateService promptTemplateService;
    private final ObjectMapper objectMapper;

    public SkillSceneRouter(SkillProperties properties,
                            SkillRegistry registry,
                            DocSkillUsageMapper usageMapper,
                            UidGenerator uidGenerator,
                            ObjectProvider<ChatModel> chatModelProvider,
                            PromptTemplateService promptTemplateService,
                            ObjectMapper objectMapper) {
        this.properties = properties;
        this.registry = registry;
        this.usageMapper = usageMapper;
        this.uidGenerator = uidGenerator;
        this.chatModelProvider = chatModelProvider;
        this.promptTemplateService = promptTemplateService;
        this.objectMapper = objectMapper;
    }

    /**
     * 路由：返回命中的技能，未命中返回 null。
     */
    public SkillMatchResult route(String question) {
        if (!properties.isEnabled() || StrUtil.isBlank(question)) {
            return null;
        }
        List<SkillDefinition> skills = registry.allEnabled();
        if (skills.isEmpty()) {
            return null;
        }
        // 优先 LLM 精确选技能；失败或未启用则回退规则打分
        if (properties.isLlmRouterEnabled()) {
            SkillMatchResult llmMatch = routeByLlm(question, skills);
            if (llmMatch != null) {
                return llmMatch;
            }
        }
        return routeByScore(question, skills);
    }

    private SkillMatchResult routeByScore(String question, List<SkillDefinition> skills) {
        SkillMatchResult best = null;
        for (SkillDefinition skill : skills) {
            double score = score(question, skill);
            if (best == null || score > best.getScore()) {
                best = new SkillMatchResult(skill, score, null);
            }
        }
        if (best == null || best.getScore() < properties.getMatchThreshold()) {
            return null;
        }
        best.setReason("命中了技能「" + displayName(best.getSkill()) + "」的适用场景（分数 " + String.format("%.2f", best.getScore()) + "）");
        log.info("技能规则路由命中: question={}, skill={}, score={}", question, best.getSkill().getName(), best.getScore());
        return best;
    }

    /** LLM 精确选技能：返回最合适的一个，都不合适返回 null。 */
    private SkillMatchResult routeByLlm(String question, List<SkillDefinition> skills) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            return null;
        }
        try {
            String skillsText = skills.stream()
                .map(s -> s.getName() + " | " + StrUtil.blankToDefault(s.getDisplayName(), s.getName())
                    + " | " + StrUtil.blankToDefault(s.getDescription(), "")
                    + " | " + StrUtil.blankToDefault(s.getWhenToUse(), ""))
                .collect(Collectors.joining("\n"));
            String prompt = promptTemplateService.render(PromptTemplateNames.SKILL_ROUTER, Map.of(
                "question", StrUtil.blankToDefault(question, ""),
                "skills", skillsText));
            String content = ChatClient.builder(chatModel).build().prompt().user(prompt).call().content();
            JsonNode root = parseJsonObject(content);
            String skillName = root == null ? "" : root.path("skillName").asText("").trim();
            if (StrUtil.isBlank(skillName) || "none".equalsIgnoreCase(skillName)) {
                return null;
            }
            SkillDefinition skill = registry.get(skillName);
            if (skill == null) {
                log.warn("技能 LLM 路由返回的技能名不存在: {}", skillName);
                return null;
            }
            SkillMatchResult result = new SkillMatchResult(skill, 100D,
                root.path("reason").asText("大模型路由命中"));
            log.info("技能 LLM 路由命中: question={}, skill={}", question, skill.getName());
            return result;
        }
        catch (Exception exception) {
            log.warn("技能 LLM 路由失败，回退规则匹配: {}", exception.getMessage());
            return null;
        }
    }

    private JsonNode parseJsonObject(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readTree(content.substring(start, end + 1));
        }
        catch (Exception exception) {
            return null;
        }
    }

    /**
     * 按名称强制指定一个已启用技能（用户通过 /skills 命令选择），不走相似度匹配。
     */
    public SkillMatchResult routeByName(String skillName) {
        if (StrUtil.isBlank(skillName)) {
            return null;
        }
        SkillDefinition skill = registry.get(skillName.trim());
        if (skill == null || skill.getRunState() == null || skill.getRunState() != 1) {
            return null;
        }
        SkillMatchResult result = new SkillMatchResult(skill, 100D, "用户通过 /skills 命令指定使用");
        log.info("技能命令指定命中: skill={}", skill.getName());
        return result;
    }

    /**
     * 记录一次技能调用（用于调用统计）。
     */
    public void recordUsage(SkillMatchResult match, String conversationId, Long exchangeId, String scene) {
        if (match == null || match.getSkill() == null || match.getSkill().getId() == null) {
            return;
        }
        try {
            DocSkillUsageEntity usage = new DocSkillUsageEntity();
            usage.setId(uidGenerator.getUid());
            usage.setSkillId(match.getSkill().getId());
            usage.setConversationId(conversationId);
            usage.setExchangeId(exchangeId);
            usage.setScene(scene);
            usage.setMatchedScore(BigDecimal.valueOf(match.getScore()));
            usage.setMatchedReason(match.getReason());
            usage.setStatus(BusinessStatus.YES.getCode());
            usageMapper.insert(usage);
        }
        catch (Exception exception) {
            log.warn("记录技能调用失败: {}", exception.getMessage());
        }
    }

    private double score(String question, SkillDefinition skill) {
        String questionText = StrUtil.blankToDefault(question, "").trim();
        if (questionText.isEmpty()) {
            return 0D;
        }
        // 主信号：技能标签关键词出现在问题中。
        // 中文场景下 bigram Jaccard 对长句分数极低（几乎永远低于阈值），标签命中才可靠。
        double keywordScore = keywordHitScore(questionText, skill.getTags());
        if (keywordScore > 0D) {
            return keywordScore;
        }
        // 回退：bigram Jaccard 相似度
        String corpus = String.join(" ",
            StrUtil.nullToEmpty(skill.getDescription()),
            StrUtil.nullToEmpty(skill.getWhenToUse()),
            StrUtil.nullToEmpty(skill.getTags()),
            StrUtil.nullToEmpty(skill.getName()),
            StrUtil.nullToEmpty(skill.getDisplayName()));
        Set<String> questionBigrams = bigrams(questionText);
        Set<String> corpusBigrams = bigrams(corpus);
        if (questionBigrams.isEmpty() || corpusBigrams.isEmpty()) {
            return 0D;
        }
        long intersection = questionBigrams.stream().filter(corpusBigrams::contains).count();
        long union = questionBigrams.size() + corpusBigrams.size() - intersection;
        return union == 0 ? 0D : (double) intersection / union;
    }

    /** 统计问题中包含的技能标签词数量；命中任意标签词即视为强匹配（返回 ≥0.5）。 */
    private double keywordHitScore(String questionText, String tags) {
        if (StrUtil.isBlank(tags)) {
            return 0D;
        }
        int hits = 0;
        for (String tag : tags.split(",")) {
            String trimmed = tag.trim();
            if (trimmed.length() >= 2 && questionText.contains(trimmed)) {
                hits++;
            }
        }
        if (hits == 0) {
            return 0D;
        }
        return 0.5D + Math.min(hits, 3) * 0.1D;
    }

    private Set<String> bigrams(String text) {
        Set<String> result = new LinkedHashSet<>();
        String normalized = StrUtil.nullToEmpty(text).trim();
        if (normalized.length() < 2) {
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
            return result;
        }
        for (int index = 0; index < normalized.length() - 1; index++) {
            result.add(normalized.substring(index, index + 2));
        }
        return result;
    }

    private String displayName(SkillDefinition skill) {
        return StrUtil.isNotBlank(skill.getDisplayName()) ? skill.getDisplayName() : skill.getName();
    }
}
