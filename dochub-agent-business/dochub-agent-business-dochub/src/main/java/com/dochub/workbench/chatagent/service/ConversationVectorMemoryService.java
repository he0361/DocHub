package com.dochub.workbench.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import lombok.extern.slf4j.Slf4j;
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
    private final ModelRuntimeRegistry modelRuntimeRegistry;
    private final UidGenerator uidGenerator;

    public ConversationVectorMemoryService(QdrantVectorStore vectorStore,
                                           ModelRuntimeRegistry modelRuntimeRegistry,
                                           UidGenerator uidGenerator) {
        this.vectorStore = vectorStore;
        this.modelRuntimeRegistry = modelRuntimeRegistry;
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
            EmbeddingRuntimeSnapshot runtime = modelRuntimeRegistry.captureEmbedding();
            float[] embedding = embed(runtime, memoryText);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("conversation_id", conversationId);
            payload.put("memory_text", memoryText);
            vectorStore.upsert(runtime.memoryCollection(),
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
            EmbeddingRuntimeSnapshot runtime = modelRuntimeRegistry.captureEmbedding();
            float[] embedding = embed(runtime, query);
            Map<String, Object> filter = Map.of("must", List.of(Map.of(
                "key", "conversation_id", "match", Map.of("value", conversationId))));
            List<QdrantVectorStore.SearchHit> hits =
                vectorStore.search(runtime.memoryCollection(), embedding, Math.max(1, topK), filter);
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

    private float[] embed(EmbeddingRuntimeSnapshot runtime, String text) {
        List<float[]> embeddings = runtime.model().embed(List.of(StrUtil.blankToDefault(text, "")));
        if (embeddings == null || embeddings.isEmpty() || embeddings.get(0) == null) {
            throw new IllegalStateException("记忆向量化为空。");
        }
        return embeddings.get(0);
    }
}
