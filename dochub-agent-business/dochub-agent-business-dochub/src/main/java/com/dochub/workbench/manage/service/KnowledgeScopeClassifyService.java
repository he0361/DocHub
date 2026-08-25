package com.dochub.workbench.manage.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;
import com.dochub.workbench.manage.mapper.DochubKnowledgeScopeNodeMapper;
import com.dochub.workbench.manage.mapper.DochubKnowledgeTopicNodeMapper;
import com.dochub.workbench.manage.model.KnowledgeClassifyResult;
import com.dochub.workbench.prompt.PromptTemplateNames;
import com.dochub.workbench.prompt.PromptTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文枢 DocHub 文档知识域/主题归类服务（LLM 驱动 + 知识结构自生长）。
 *
 * <p>解析阶段为文档打知识域（scope）/主题（topic）标签时，不再使用硬编码关键词猜测，
 * 而是把「文档内容 + 当前配置的知识域/主题」交给 LLM 判定：
 * <ul>
 *   <li>能归属现有知识域 → 复用其 code；</li>
 *   <li>没有合适知识域 → 基于文档生成新知识域并持久化到 scope 节点表；</li>
 *   <li>有知识域但无合适主题 → 生成新主题并挂到该知识域，持久化到 topic 节点表。</li>
 * </ul>
 * 从而让学术论文这类非业务文档也能得到合理归类，而不是被硬塞进业务知识域。</p>
 */
@Slf4j
@Service
public class KnowledgeScopeClassifyService {

    private static final int EXCERPT_MAX_CHARS = 1500;

    private final DochubKnowledgeScopeNodeMapper scopeNodeMapper;
    private final DochubKnowledgeTopicNodeMapper topicNodeMapper;
    private final PromptTemplateService promptTemplateService;
    private final ObjectProvider<ChatModel> chatModelProvider;
    private final UidGenerator uidGenerator;
    private final ObjectMapper objectMapper;

    public KnowledgeScopeClassifyService(DochubKnowledgeScopeNodeMapper scopeNodeMapper,
                                         DochubKnowledgeTopicNodeMapper topicNodeMapper,
                                         PromptTemplateService promptTemplateService,
                                         ObjectProvider<ChatModel> chatModelProvider,
                                         UidGenerator uidGenerator,
                                         ObjectMapper objectMapper) {
        this.scopeNodeMapper = scopeNodeMapper;
        this.topicNodeMapper = topicNodeMapper;
        this.promptTemplateService = promptTemplateService;
        this.chatModelProvider = chatModelProvider;
        this.uidGenerator = uidGenerator;
        this.objectMapper = objectMapper;
    }

    /**
     * 对文档做知识域/主题归类；LLM 不可用或返回异常时返回 null，由调用方回退到规则判定。
     */
    public KnowledgeClassifyResult classify(String documentName, List<String> sectionTitles, String parsedText) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            log.warn("未找到可用的 ChatModel，跳过 LLM 知识分类。");
            return null;
        }
        try {
            List<DochubKnowledgeScopeNode> scopes = scopeNodeMapper.selectList(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
                .eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(DochubKnowledgeScopeNode::getSortOrder));
            List<DochubKnowledgeTopicNode> topics = topicNodeMapper.selectList(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
                .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(DochubKnowledgeTopicNode::getSortOrder));

            String scopeListText = CollUtil.isEmpty(scopes)
                ? "（暂无知识域，全部需要新生成）"
                : scopes.stream()
                .map(scope -> joinScope(scope))
                .collect(Collectors.joining("\n"));
            String topicListText = CollUtil.isEmpty(topics)
                ? "（暂无主题）"
                : topics.stream()
                .map(this::joinTopic)
                .collect(Collectors.joining("\n"));
            String sectionText = CollUtil.isEmpty(sectionTitles)
                ? "（无章节标题）"
                : sectionTitles.stream().filter(StrUtil::isNotBlank).distinct().limit(8).collect(Collectors.joining("；"));

            String prompt = promptTemplateService.render(PromptTemplateNames.KNOWLEDGE_SCOPE_CLASSIFY, Map.of(
                "scopeList", scopeListText,
                "topicList", topicListText,
                "documentName", StrUtil.blankToDefault(documentName, "未命名文档"),
                "sectionTitles", sectionText,
                "documentExcerpt", truncateExcerpt(parsedText)));

            String content = ChatClient.builder(chatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
            JsonNode root = parseJsonObject(content);
            if (root == null) {
                log.warn("LLM 知识分类返回内容无法解析为 JSON，documentName={}", documentName);
                return null;
            }

            JsonNode scopeNode = root.path("scope");
            JsonNode topicNode = root.path("topic");
            String scopeMatchType = asText(scopeNode.path("matchType"));
            String rawScopeCode = asText(scopeNode.path("code"));
            String scopeName = asText(scopeNode.path("name"));
            String topicMatchType = asText(topicNode.path("matchType"));
            String rawTopicCode = asText(topicNode.path("code"));
            String topicName = asText(topicNode.path("name"));
            String businessCategory = asText(root.path("businessCategory"));
            String reason = asText(root.path("reason"));

            if (StrUtil.isBlank(rawScopeCode)) {
                log.warn("LLM 知识分类未返回 scope code，documentName={}", documentName);
                return null;
            }

            boolean scopeNew = "new".equals(scopeMatchType);
            String scopeCode = ensureScope(rawScopeCode, scopeName, scopeNew, reason);
            boolean topicNew = "new".equals(topicMatchType);
            String topicCode = StrUtil.isBlank(rawTopicCode) ? "" : ensureTopic(scopeCode, rawTopicCode, topicName, topicNew);

            return new KnowledgeClassifyResult(
                scopeCode,
                StrUtil.isNotBlank(scopeName) ? scopeName : rawScopeCode,
                scopeNew,
                topicCode,
                topicName,
                topicNew,
                businessCategory,
                reason);
        }
        catch (Exception exception) {
            log.warn("LLM 知识分类执行失败，回退到规则判定。documentName={}, error={}", documentName, exception.getMessage());
            return null;
        }
    }

    /** 复用或创建知识域：优先按 code 去重，其次按名称去重，避免重复建域。 */
    private String ensureScope(String code, String name, boolean createNew, String reason) {
        DochubKnowledgeScopeNode existingByCode = scopeNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
            .eq(DochubKnowledgeScopeNode::getScopeCode, code)
            .eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        if (existingByCode != null) {
            return existingByCode.getScopeCode();
        }
        if (StrUtil.isNotBlank(name)) {
            DochubKnowledgeScopeNode existingByName = scopeNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
                .eq(DochubKnowledgeScopeNode::getScopeName, name)
                .eq(DochubKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode())
                .last("LIMIT 1"));
            if (existingByName != null) {
                return existingByName.getScopeCode();
            }
        }
        if (!createNew) {
            // LLM 判定为现有知识域但库里没有：不误建，直接沿用该 code。
            return code;
        }
        DochubKnowledgeScopeNode node = new DochubKnowledgeScopeNode();
        node.setId(uidGenerator.getUid());
        node.setScopeCode(code);
        node.setScopeName(StrUtil.blankToDefault(name, code));
        node.setDescription(StrUtil.isNotBlank(reason) ? "文档自动归类生成：" + reason : "文档自动归类生成。");
        node.setSortOrder(nextScopeSortOrder());
        node.setStatus(BusinessStatus.YES.getCode());
        node.setCreateTime(new Date());
        node.setEditTime(new Date());
        scopeNodeMapper.insert(node);
        log.info("知识域自生长：新增知识域 code={}, name={}", code, StrUtil.blankToDefault(name, code));
        return code;
    }

    /** 复用或创建主题（挂到指定知识域下），同样按 code/名称去重。 */
    private String ensureTopic(String scopeCode, String code, String name, boolean createNew) {
        if (StrUtil.isBlank(code)) {
            return "";
        }
        DochubKnowledgeTopicNode existingByCode = topicNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
            .eq(DochubKnowledgeTopicNode::getTopicCode, code)
            .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
            .last("LIMIT 1"));
        if (existingByCode != null) {
            return existingByCode.getTopicCode();
        }
        if (StrUtil.isNotBlank(name)) {
            DochubKnowledgeTopicNode existingByName = topicNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
                .eq(DochubKnowledgeTopicNode::getTopicName, name)
                .eq(DochubKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode())
                .last("LIMIT 1"));
            if (existingByName != null) {
                return existingByName.getTopicCode();
            }
        }
        if (!createNew) {
            return code;
        }
        DochubKnowledgeTopicNode node = new DochubKnowledgeTopicNode();
        node.setId(uidGenerator.getUid());
        node.setTopicCode(code);
        node.setTopicName(StrUtil.blankToDefault(name, code));
        node.setScopeCode(scopeCode);
        node.setSortOrder(nextTopicSortOrder());
        node.setStatus(BusinessStatus.YES.getCode());
        node.setCreateTime(new Date());
        node.setEditTime(new Date());
        topicNodeMapper.insert(node);
        log.info("知识域自生长：新增主题 code={}, name={}, scopeCode={}", code, StrUtil.blankToDefault(name, code), scopeCode);
        return code;
    }

    private String joinScope(DochubKnowledgeScopeNode scope) {
        return scope.getScopeCode() + " | " + StrUtil.blankToDefault(scope.getScopeName(), "")
            + " | " + StrUtil.blankToDefault(scope.getDescription(), "");
    }

    private String joinTopic(DochubKnowledgeTopicNode topic) {
        return topic.getTopicCode() + " | " + StrUtil.blankToDefault(topic.getTopicName(), "")
            + " | " + StrUtil.blankToDefault(topic.getScopeCode(), "")
            + " | " + StrUtil.blankToDefault(topic.getDescription(), "");
    }

    private Integer nextScopeSortOrder() {
        DochubKnowledgeScopeNode max = scopeNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeScopeNode>()
            .orderByDesc(DochubKnowledgeScopeNode::getSortOrder)
            .last("LIMIT 1"));
        return (max == null || max.getSortOrder() == null) ? 1 : max.getSortOrder() + 1;
    }

    private Integer nextTopicSortOrder() {
        DochubKnowledgeTopicNode max = topicNodeMapper.selectOne(new LambdaQueryWrapper<DochubKnowledgeTopicNode>()
            .orderByDesc(DochubKnowledgeTopicNode::getSortOrder)
            .last("LIMIT 1"));
        return (max == null || max.getSortOrder() == null) ? 1 : max.getSortOrder() + 1;
    }

    private String truncateExcerpt(String parsedText) {
        // 去掉尖括号，避免论文正文中的 < > 与 ST 模板分隔符产生边界情况。
        String text = StrUtil.blankToDefault(parsedText, "")
            .replace('<', ' ')
            .replace('>', ' ')
            .replaceAll("\\s+", " ")
            .trim();
        return text.length() > EXCERPT_MAX_CHARS ? text.substring(0, EXCERPT_MAX_CHARS) : text;
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

    private String asText(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("").trim();
    }
}
