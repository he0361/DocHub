package com.dochub.workbench.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文枢 DocHub 会话长期记忆向量服务（Qdrant）。
 *
 * <p>长期记忆：把压缩后的会话历史摘要向量化，存入 Qdrant；
 * 用户提问时，对当前问题做向量检索，召回相似的历史记忆注入提示词。</p>
 */
@Slf4j
@Service
public class ConversationVectorMemoryService {

    private final QdrantVectorStore vectorStore;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final UidGenerator uidGenerator;

    public ConversationVectorMemoryService(QdrantVectorStore vectorStore,
                                           ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                           UidGenerator uidGenerator) {
        this.vectorStore = vectorStore;
        this.embeddingModelProvider = embeddingModelProvider;
        this.uidGenerator = uidGenerator;
    }

    /**
     * 把一段记忆文本向量化并存入长期记忆。
     */
    public void saveMemory(String conversationId, String memoryText) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(memoryText)) {
            return;
        }
        try {
            float[] embedding = embed(memoryText);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversation_id", conversationId);
            payload.put("memory_text", memoryText);
            vectorStore.upsert(vectorStore.memoryCollection(),
                List.of(new QdrantVectorStore.Point(uidGenerator.getUid(), embedding, payload)));
        }
        catch (Exception exception) {
            log.warn("保存会话长期记忆失败: {}", exception.getMessage());
        }
    }

    /**
     * 用当前问题在长期记忆中做相似度检索，返回命中的记忆文本。
     */
    public List<String> retrieveMemories(String conversationId, String query, int topK) {
        if (StrUtil.isBlank(conversationId) || StrUtil.isBlank(query)) {
            return List.of();
        }
        try {
            float[] embedding = embed(query);
            Map<String, Object> filter = Map.of("must", List.of(Map.of(
                "key", "conversation_id", "match", Map.of("value", conversationId))));
            List<QdrantVectorStore.SearchHit> hits =
                vectorStore.search(vectorStore.memoryCollection(), embedding, Math.max(1, topK), filter);
            List<String> memories = new ArrayList<>();
            for (QdrantVectorStore.SearchHit hit : hits) {
                Object text = hit.payload().get("memory_text");
                if (text != null && StrUtil.isNotBlank(text.toString())) {
                    memories.add(text.toString());
                }
            }
            return memories;
        }
        catch (Exception exception) {
            log.warn("检索会话长期记忆失败: {}", exception.getMessage());
            return List.of();
        }
    }

    private float[] embed(String text) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("当前无可用 EmbeddingModel，无法向量化记忆。");
        }
        List<float[]> embeddings = model.embed(List.of(StrUtil.blankToDefault(text, "")));
        if (embeddings == null || embeddings.isEmpty() || embeddings.get(0) == null) {
            throw new IllegalStateException("记忆向量化为空。");
        }
        return embeddings.get(0);
    }
}
