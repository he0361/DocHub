package com.dochub.workbench.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import com.dochub.workbench.chatagent.data.DochubChatDialogue;
import com.dochub.workbench.chatagent.data.DochubChatExchange;
import com.dochub.workbench.chatagent.mapper.DochubChatDialogueMapper;
import com.dochub.workbench.chatagent.mapper.DochubChatExchangeMapper;
import com.dochub.workbench.chatagent.model.ConversationExchangeView;
import com.dochub.workbench.chatagent.model.SearchReference;
import com.dochub.workbench.chatagent.model.debug.ChatDebugTrace;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.ChatQueryMode;
import org.javaup.enums.ChatSessionStatus;
import org.javaup.enums.ChatTurnStatus;
import org.javaup.util.DateUtils;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

@Repository
public class MybatisConversationArchiveStore implements ConversationArchiveStore {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<SearchReference>> REFERENCE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<ChatDebugTrace> DEBUG_TRACE_TYPE = new TypeReference<>() {
    };

    private final DochubChatDialogueMapper dialogueMapper;
    private final DochubChatExchangeMapper exchangeMapper;
    private final ObjectMapper objectMapper;

    @Resource
    private UidGenerator uidGenerator;

    public MybatisConversationArchiveStore(DochubChatDialogueMapper dialogueMapper,
                                           DochubChatExchangeMapper exchangeMapper,
                                           ObjectMapper objectMapper) {
        this.dialogueMapper = dialogueMapper;
        this.exchangeMapper = exchangeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationExchangeView startExchange(String conversationId,
                                                  String question,
                                                  ChatQueryMode chatMode,
                                                  Long selectedDocumentId,
                                                  String selectedDocumentName) {
        upsertDialogue(conversationId, ChatSessionStatus.RUNNING, chatMode, selectedDocumentId, selectedDocumentName);

        long exchangeId = uidGenerator.getUid();

        DochubChatExchange exchange = new DochubChatExchange();

        exchange.setId(exchangeId);
        exchange.setConversationId(conversationId);
        exchange.setQuestion(question);
        exchange.setAnswer("");
        exchange.setThinkingSteps(writeJson(List.of()));
        exchange.setReferenceList(writeJson(List.of()));
        exchange.setRecommendationList(writeJson(List.of()));
        exchange.setUsedToolList(writeJson(List.of()));
        exchange.setDebugTraceJson(null);
        exchange.setTurnStatus(ChatTurnStatus.RUNNING.getCode());
        exchange.setErrorMessage("");
        exchange.setFirstResponseTimeMs(null);
        exchange.setTotalResponseTimeMs(null);
        exchange.setStatus(BusinessStatus.YES.getCode());
        exchangeMapper.insert(exchange);

        return new ConversationExchangeView(
            exchangeId,
            question,
            "",
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null,
            ChatTurnStatus.RUNNING,
            "",
            null,
            null,
             DateUtils.now(),
             DateUtils.now()
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSessionScope(String conversationId,
                                    ChatQueryMode chatMode,
                                    Long selectedDocumentId,
                                    String selectedDocumentName) {
        upsertDialogue(conversationId, ChatSessionStatus.RUNNING, chatMode, selectedDocumentId, selectedDocumentName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeExchange(String conversationId,
                                 long exchangeId,
                                 String answer,
                                 List<String> thinkingSteps,
                                 List<SearchReference> references,
                                 List<String> recommendations,
                                 List<String> usedTools,
                                 ChatDebugTrace debugTrace,
                                 ChatTurnStatus status,
                                 String errorMessage,
                                 Long firstResponseTimeMs,
                                 Long totalResponseTimeMs) {

        DochubChatExchange existingExchange = exchangeMapper.selectOne(
            new LambdaQueryWrapper<DochubChatExchange>()
                .eq(DochubChatExchange::getId, exchangeId)
                .eq(DochubChatExchange::getConversationId, conversationId)
                .last("LIMIT 1")
        );
        if (existingExchange == null) {

            return;
        }

        DochubChatExchange updateExchange = new DochubChatExchange();
        updateExchange.setId(exchangeId);
        updateExchange.setAnswer(safeText(answer));
        updateExchange.setThinkingSteps(writeJson(thinkingSteps));
        updateExchange.setReferenceList(writeJson(references));
        updateExchange.setRecommendationList(writeJson(recommendations));
        updateExchange.setUsedToolList(writeJson(usedTools));
        updateExchange.setDebugTraceJson(writeNullableJson(debugTrace));
        updateExchange.setTurnStatus(status.getCode());
        updateExchange.setErrorMessage(safeText(errorMessage));
        updateExchange.setFirstResponseTimeMs(firstResponseTimeMs);
        updateExchange.setTotalResponseTimeMs(totalResponseTimeMs);
        exchangeMapper.updateById(updateExchange);

        dialogueMapper.update(
            null,
            new LambdaUpdateWrapper<DochubChatDialogue>()
                .eq(DochubChatDialogue::getConversationId, conversationId)

                .set(DochubChatDialogue::getSessionStatus, ChatSessionStatus.IDLE.getCode())
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationArchiveRecord> getSessionRecord(String conversationId) {

        DochubChatDialogue dialogue = dialogueMapper.selectOne(
            activeDialogueByConversation(conversationId)
                .orderByDesc(DochubChatDialogue::getId)
                .last("LIMIT 1")
        );
        if (dialogue == null) {
            return Optional.empty();
        }

        List<ConversationExchangeView> exchanges = loadExchangeViews(List.of(conversationId))
            .getOrDefault(conversationId, List.of());

        return Optional.of(new ConversationArchiveRecord(
            dialogue.getConversationId(),
            ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
            resolveChatMode(dialogue),
            dialogue.getSelectedDocumentId(),
            safeText(dialogue.getSelectedDocumentName()),
            toInstant(dialogue.getCreateTime()),
            toInstant(dialogue.getEditTime()),
            exchanges
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeView> listExchanges(String conversationId) {

        return selectConversationExchanges(
            new LambdaQueryWrapper<DochubChatExchange>()
                .eq(DochubChatExchange::getConversationId, conversationId)
                .orderByAsc(DochubChatExchange::getCreateTime)
                .orderByAsc(DochubChatExchange::getId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeView> listExchangesAfter(String conversationId, long afterExchangeId) {

        return selectConversationExchanges(
            new LambdaQueryWrapper<DochubChatExchange>()
                .eq(DochubChatExchange::getConversationId, conversationId)
                .gt(afterExchangeId > 0, DochubChatExchange::getId, afterExchangeId)
                .orderByAsc(DochubChatExchange::getCreateTime)
                .orderByAsc(DochubChatExchange::getId)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationExchangeView> listRecentExchanges(String conversationId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        List<DochubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<DochubChatExchange>()
                .eq(DochubChatExchange::getConversationId, conversationId)
                .orderByDesc(DochubChatExchange::getCreateTime)
                .orderByDesc(DochubChatExchange::getId)
                .last("LIMIT " + limit)
        );
        if (exchanges == null || exchanges.isEmpty()) {
            return List.of();
        }
        List<ConversationExchangeView> views = new ArrayList<>(exchanges.size());
        for (int index = exchanges.size() - 1; index >= 0; index--) {
            views.add(toExchangeView(exchanges.get(index)));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationArchiveRecord> listSessionRecords() {
        List<DochubChatDialogue> rawDialogues = dialogueMapper.selectList(
            new LambdaQueryWrapper<DochubChatDialogue>()
                .orderByDesc(DochubChatDialogue::getEditTime)
                .orderByDesc(DochubChatDialogue::getId)
        );
        if (rawDialogues == null || rawDialogues.isEmpty()) {
            return List.of();
        }

        Map<String, DochubChatDialogue> latestDialogues = new LinkedHashMap<>();
        for (DochubChatDialogue dialogue : rawDialogues) {

            latestDialogues.putIfAbsent(dialogue.getConversationId(), dialogue);
        }
        List<DochubChatDialogue> dialogues = new ArrayList<>(latestDialogues.values());

        List<String> conversationIds = dialogues.stream()
            .map(DochubChatDialogue::getConversationId)
            .toList();

        Map<String, List<ConversationExchangeView>> exchangeViewMap = loadExchangeViews(conversationIds);

        List<ConversationArchiveRecord> result = new ArrayList<>(dialogues.size());
        for (DochubChatDialogue dialogue : dialogues) {
            result.add(new ConversationArchiveRecord(
                dialogue.getConversationId(),
                ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
                resolveChatMode(dialogue),
                dialogue.getSelectedDocumentId(),
                safeText(dialogue.getSelectedDocumentName()),
                toInstant(dialogue.getCreateTime()),
                toInstant(dialogue.getEditTime()),
                exchangeViewMap.getOrDefault(dialogue.getConversationId(), List.of())
            ));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationArchivePage listSessionRecordPage(int pageNo,
                                                         int pageSize,
                                                         String keyword,
                                                         ChatQueryMode chatMode,
                                                         ChatTurnStatus latestTurnStatus) {
        int resolvedPageNo = Math.max(pageNo, 1);
        int resolvedPageSize = Math.max(pageSize, 1);

        LambdaQueryWrapper<DochubChatDialogue> wrapper = new LambdaQueryWrapper<DochubChatDialogue>()
            .orderByDesc(DochubChatDialogue::getEditTime)
            .orderByDesc(DochubChatDialogue::getId);
        applySessionPageFilters(wrapper, keyword, chatMode, latestTurnStatus);

        Page<DochubChatDialogue> page = new Page<>(resolvedPageNo, resolvedPageSize);
        IPage<DochubChatDialogue> resultPage = dialogueMapper.selectPage(
            page,
            wrapper
        );

        List<String> conversationIds = resultPage.getRecords().stream()
            .map(DochubChatDialogue::getConversationId)
            .toList();
        Map<String, ConversationExchangeView> latestExchangeMap = loadLatestExchangeMap(conversationIds);

        List<ConversationArchiveRecord> records = resultPage.getRecords().stream()
            .map(dialogue -> new ConversationArchiveRecord(
                dialogue.getConversationId(),
                ChatSessionStatus.isRunning(dialogue.getSessionStatus()),
                resolveChatMode(dialogue),
                dialogue.getSelectedDocumentId(),
                safeText(dialogue.getSelectedDocumentName()),
                toInstant(dialogue.getCreateTime()),
                toInstant(dialogue.getEditTime()),
                latestExchangeMap.containsKey(dialogue.getConversationId())
                    ? List.of(latestExchangeMap.get(dialogue.getConversationId()))
                    : List.of()
            ))
            .toList();

        return new ConversationArchivePage(
            resultPage.getCurrent(),
            resultPage.getSize(),
            resultPage.getTotal(),
            records
        );
    }

    @Override
    @Transactional
    public ConversationArchiveStore.ConversationRemovalResult deleteSession(String conversationId) {
        LambdaQueryWrapper<DochubChatExchange> exchangeQuery = exchangesByConversation(conversationId);
        LambdaQueryWrapper<DochubChatDialogue> dialogueQuery = activeDialogueByConversation(conversationId);

        int removedExchangeCount = toInt(exchangeMapper.selectCount(exchangeQuery));
        int removedDialogueCount = toInt(dialogueMapper.selectCount(dialogueQuery));

        if (removedExchangeCount > 0) {

            exchangeMapper.delete(exchangesByConversation(conversationId));
        }
        if (removedDialogueCount > 0) {
            dialogueMapper.delete(activeDialogueByConversation(conversationId));
        }

        return new ConversationArchiveStore.ConversationRemovalResult(removedDialogueCount, removedExchangeCount);
    }

    private void upsertDialogue(String conversationId,
                                ChatSessionStatus dialogueStage,
                                ChatQueryMode chatMode,
                                Long selectedDocumentId,
                                String selectedDocumentName) {
        Objects.requireNonNull(chatMode, "chatMode 不能为空");
        DochubChatDialogue dialogue = dialogueMapper.selectOne(
            activeDialogueByConversation(conversationId)
                .orderByDesc(DochubChatDialogue::getId)
                .last("LIMIT 1")
        );

        if (dialogue == null) {
            DochubChatDialogue newDialogue = new DochubChatDialogue();
            newDialogue.setId(uidGenerator.getUid());
            newDialogue.setConversationId(conversationId);
            newDialogue.setSessionStatus(dialogueStage.getCode());
            newDialogue.setChatMode(chatMode.getCode());
            newDialogue.setSelectedDocumentId(selectedDocumentId);
            newDialogue.setSelectedDocumentName(selectedDocumentName);
            newDialogue.setStatus(BusinessStatus.YES.getCode());

            dialogueMapper.insert(newDialogue);
            return;
        }

        boolean stageChanged = !dialogueStage.equals(ChatSessionStatus.fromCode(dialogue.getSessionStatus()));
        boolean chatModeChanged = !Objects.equals(chatMode.getCode(), dialogue.getChatMode());
        boolean documentScopeChanged = !Objects.equals(selectedDocumentId, dialogue.getSelectedDocumentId())
            || !Objects.equals(safeText(selectedDocumentName), safeText(dialogue.getSelectedDocumentName()));

        if (stageChanged || chatModeChanged || documentScopeChanged) {
            DochubChatDialogue updateDialogue = new DochubChatDialogue();
            updateDialogue.setId(dialogue.getId());
            updateDialogue.setSessionStatus(dialogueStage.getCode());
            updateDialogue.setChatMode(chatMode.getCode());
            updateDialogue.setSelectedDocumentId(selectedDocumentId);
            updateDialogue.setSelectedDocumentName(selectedDocumentName);
            dialogueMapper.updateById(updateDialogue);
        }
    }

    private ChatQueryMode resolveChatMode(DochubChatDialogue dialogue) {
        if (dialogue == null || dialogue.getChatMode() == null) {
            throw new IllegalStateException("会话记录缺少 chatMode，当前教学版项目要求数据库使用最新结构");
        }
        return ChatQueryMode.fromCode(dialogue.getChatMode());
    }

    private Map<String, List<ConversationExchangeView>> loadExchangeViews(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }

        List<DochubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<DochubChatExchange>()
                .in(DochubChatExchange::getConversationId, conversationIds)

                .orderByAsc(DochubChatExchange::getCreateTime)
                .orderByAsc(DochubChatExchange::getConversationId)
                .orderByAsc(DochubChatExchange::getId)
        );

        Map<String, List<ConversationExchangeView>> exchangeViewsByConversation = new LinkedHashMap<>();
        for (DochubChatExchange exchange : exchanges) {

            exchangeViewsByConversation.computeIfAbsent(exchange.getConversationId(), key -> new ArrayList<>())
                .add(toExchangeView(exchange));
        }
        return exchangeViewsByConversation;
    }

    private void applySessionPageFilters(LambdaQueryWrapper<DochubChatDialogue> wrapper,
                                         String keyword,
                                         ChatQueryMode chatMode,
                                         ChatTurnStatus latestTurnStatus) {
        if (wrapper == null) {
            return;
        }
        if (chatMode != null) {
            wrapper.eq(DochubChatDialogue::getChatMode, chatMode.getCode());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String likeKeyword = "%" + keyword.trim() + "%";
            wrapper.and(query -> query
                .like(DochubChatDialogue::getConversationId, keyword.trim())
                .or()
                .like(DochubChatDialogue::getSelectedDocumentName, keyword.trim())
                .or()
                .apply(
                    "EXISTS (SELECT 1 FROM dochub_chat_exchange e WHERE e.dialogue_code = dochub_chat_dialogue.dialogue_code"
                        + " AND (e.user_prompt LIKE {0} OR e.reply_content LIKE {0} OR e.finish_note LIKE {0}))",
                    likeKeyword
                )
            );
        }
        if (latestTurnStatus == null) {
            return;
        }
        if (latestTurnStatus == ChatTurnStatus.RUNNING) {
            wrapper.eq(DochubChatDialogue::getSessionStatus, ChatSessionStatus.RUNNING.getCode());
            return;
        }
        wrapper.eq(DochubChatDialogue::getSessionStatus, ChatSessionStatus.IDLE.getCode());
        wrapper.apply(
            "EXISTS (SELECT 1 FROM dochub_chat_exchange e"
                + " WHERE e.dialogue_code = dochub_chat_dialogue.dialogue_code"
                + " AND e.id = (SELECT latest.id FROM dochub_chat_exchange latest"
                + " WHERE latest.dialogue_code = dochub_chat_dialogue.dialogue_code"
                + " ORDER BY latest.create_time DESC, latest.id DESC LIMIT 1)"
                + " AND e.exchange_state = {0})",
            latestTurnStatus.getCode()
        );
    }

    private Map<String, ConversationExchangeView> loadLatestExchangeMap(List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return Map.of();
        }
        List<DochubChatExchange> exchanges = exchangeMapper.selectList(
            new LambdaQueryWrapper<DochubChatExchange>()
                .in(DochubChatExchange::getConversationId, conversationIds)
                .orderByDesc(DochubChatExchange::getCreateTime)
                .orderByDesc(DochubChatExchange::getId)
        );
        if (exchanges == null || exchanges.isEmpty()) {
            return Map.of();
        }

        Map<String, ConversationExchangeView> latestExchangeMap = new LinkedHashMap<>();
        for (DochubChatExchange exchange : exchanges) {
            if (exchange == null || latestExchangeMap.containsKey(exchange.getConversationId())) {
                continue;
            }
            latestExchangeMap.put(exchange.getConversationId(), toExchangeView(exchange));
        }
        return latestExchangeMap;
    }

    private List<ConversationExchangeView> selectConversationExchanges(LambdaQueryWrapper<DochubChatExchange> queryWrapper) {
        List<DochubChatExchange> exchanges = exchangeMapper.selectList(queryWrapper);
        if (exchanges == null || exchanges.isEmpty()) {
            return List.of();
        }
        List<ConversationExchangeView> result = new ArrayList<>(exchanges.size());
        for (DochubChatExchange exchange : exchanges) {
            result.add(toExchangeView(exchange));
        }
        return result;
    }

    private ConversationExchangeView toExchangeView(DochubChatExchange exchange) {

        return new ConversationExchangeView(
            exchange.getId(),
            safeText(exchange.getQuestion()),
            safeText(exchange.getAnswer()),
            readStringList(exchange.getThinkingSteps()),
            readReferenceList(exchange.getReferenceList()),
            readStringList(exchange.getRecommendationList()),
            readStringList(exchange.getUsedToolList()),
            readDebugTrace(exchange.getDebugTraceJson()),
            ChatTurnStatus.fromCode(exchange.getTurnStatus()),
            safeText(exchange.getErrorMessage()),
            exchange.getFirstResponseTimeMs(),
            exchange.getTotalResponseTimeMs(),
            exchange.getCreateTime(),
            exchange.getEditTime()
        );
    }

    private List<String> readStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {

            return objectMapper.readValue(json, STRING_LIST_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析字符串列表失败", exception);
        }
    }

    private List<SearchReference> readReferenceList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {

            return objectMapper.readValue(json, REFERENCE_LIST_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析引用来源列表失败", exception);
        }
    }

    private ChatDebugTrace readDebugTrace(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        try {

            return objectMapper.readValue(json, DEBUG_TRACE_TYPE);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析调试轨迹失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {

            return objectMapper.writeValueAsString(value != null ? value : List.of());
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化会话字段失败", exception);
        }
    }

    private String writeNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化可空会话字段失败", exception);
        }
    }

    private Instant toInstant(Date date) {

        return date != null ? date.toInstant() : null;
    }

    private String safeText(String text) {

        return text != null ? text : "";
    }

    private LambdaQueryWrapper<DochubChatDialogue> activeDialogueByConversation(String conversationId) {

        return new LambdaQueryWrapper<DochubChatDialogue>()
            .eq(DochubChatDialogue::getConversationId, conversationId);
    }

    private LambdaQueryWrapper<DochubChatExchange> exchangesByConversation(String conversationId) {

        return new LambdaQueryWrapper<DochubChatExchange>()
            .eq(DochubChatExchange::getConversationId, conversationId);
    }

    private int toInt(Long count) {

        return count == null ? 0 : count.intValue();
    }
}
