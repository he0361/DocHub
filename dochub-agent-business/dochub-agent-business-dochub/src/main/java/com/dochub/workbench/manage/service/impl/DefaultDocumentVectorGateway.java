package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.service.DocumentVectorGateway;
import com.dochub.workbench.manage.support.DocumentIndexBuildProgressService;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.DocumentManageCode;
import org.javaup.enums.DocumentVectorStatusEnum;
import org.javaup.enums.DocumentVectorStoreTypeEnum;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 文枢 DocHub 文档向量网关（Qdrant 实现）。
 */
@Slf4j
@Service
public class DefaultDocumentVectorGateway implements DocumentVectorGateway {

    public static final int EMBEDDING_BATCH_SIZE_LIMIT = 10;

    private final QdrantVectorStore vectorStore;
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    private final DocumentIndexBuildProgressService indexBuildProgressService;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModelName;

    public DefaultDocumentVectorGateway(QdrantVectorStore vectorStore,
                                        ObjectProvider<EmbeddingModel> embeddingModelProvider,
                                        DocumentIndexBuildProgressService indexBuildProgressService) {
        this.vectorStore = vectorStore;
        this.embeddingModelProvider = embeddingModelProvider;
        this.indexBuildProgressService = indexBuildProgressService;
    }

    @Override
    public void vectorize(List<DochubDocumentChunk> chunkList) {
        if (CollUtil.isEmpty(chunkList)) {
            return;
        }
        EmbeddingModel embeddingModel = requireEmbeddingModel();
        List<DochubDocumentChunk> validChunkList = chunkList.stream()
            .filter(chunk -> chunk != null && StrUtil.isNotBlank(chunk.getChunkText()))
            .toList();
        if (validChunkList.isEmpty()) {
            return;
        }
        String currentEmbeddingModelName = resolveEmbeddingModelName();
        int batchSize = EMBEDDING_BATCH_SIZE_LIMIT;
        int totalBatchCount = (validChunkList.size() + batchSize - 1) / batchSize;
        Long documentId = validChunkList.get(0).getDocumentId();
        Long taskId = validChunkList.get(0).getTaskId();
        log.info("开始执行文档向量化(Qdrant)，chunkCount={}, batchCount={}, embeddingModel={}",
            validChunkList.size(), totalBatchCount, currentEmbeddingModelName);

        for (int startIndex = 0; startIndex < validChunkList.size(); startIndex += batchSize) {
            int endIndex = Math.min(startIndex + batchSize, validChunkList.size());
            List<DochubDocumentChunk> currentBatch = validChunkList.subList(startIndex, endIndex);
            int currentBatchIndex = (startIndex / batchSize) + 1;
            log.info("Qdrant 向量化批次 batchIndex={}/{}，batchSize={}",
                currentBatchIndex, totalBatchCount, currentBatch.size());
            indexBuildProgressService.reportVectorizeBatch(documentId, taskId, currentBatchIndex, totalBatchCount);

            List<float[]> embeddingList = embeddingModel.embed(currentBatch.stream()
                .map(DochubDocumentChunk::getChunkText)
                .toList());
            if (embeddingList.size() != currentBatch.size()) {
                throw new IllegalStateException("EmbeddingModel 返回的向量数量与 chunk 数量不一致。");
            }
            // 首次写入前建好文档集合 + 文本字段全文索引（幂等）
            vectorStore.ensureDocumentCollection(embeddingList.get(0).length);

            List<QdrantVectorStore.Point> points = new ArrayList<>();
            for (int index = 0; index < currentBatch.size(); index++) {
                DochubDocumentChunk chunk = currentBatch.get(index);
                points.add(new QdrantVectorStore.Point(chunk.getId(), embeddingList.get(index),
                    buildPayload(chunk, currentEmbeddingModelName)));
            }
            vectorStore.upsert(vectorStore.documentCollection(), points);
            markSuccess(currentBatch);
        }
        log.info("文档向量化(Qdrant)完成，chunkCount={}", validChunkList.size());
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        try {
            Map<String, Object> filter = Map.of("must", List.of(Map.of(
                "key", "document_id", "match", Map.of("value", documentId))));
            vectorStore.deleteByFilter(vectorStore.documentCollection(), filter);
        }
        catch (Exception exception) {
            throw new DochubFrameException(DocumentManageCode.DOCUMENT_VECTOR_FAILED.getCode(),
                "删除 Qdrant 向量失败: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildPayload(DochubDocumentChunk chunk, String embeddingModelName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document_id", chunk.getDocumentId());
        payload.put("task_id", chunk.getTaskId());
        if (chunk.getPlanId() != null) {
            payload.put("plan_id", chunk.getPlanId());
        }
        if (chunk.getParentBlockId() != null) {
            payload.put("parent_block_id", chunk.getParentBlockId());
        }
        payload.put("chunk_no", defaultInteger(chunk.getChunkNo()));
        payload.put("source_type", defaultInteger(chunk.getSourceType()));
        payload.put("section_path", StrUtil.nullToEmpty(chunk.getSectionPath()));
        if (chunk.getStructureNodeId() != null) {
            payload.put("structure_node_id", chunk.getStructureNodeId());
        }
        payload.put("structure_node_type", defaultInteger(chunk.getStructureNodeType()));
        payload.put("canonical_path", StrUtil.nullToEmpty(chunk.getCanonicalPath()));
        payload.put("item_index", defaultInteger(chunk.getItemIndex()));
        payload.put("chunk_text", StrUtil.nullToEmpty(chunk.getChunkText()));
        payload.put("embedding_model", embeddingModelName);
        return payload;
    }

    private void markSuccess(List<DochubDocumentChunk> chunkBatch) {
        for (DochubDocumentChunk chunk : chunkBatch) {
            chunk.setVectorId(String.valueOf(chunk.getId()));
            chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.QDRANT.getCode());
            chunk.setVectorStatus(DocumentVectorStatusEnum.VECTOR_SUCCESS.getCode());
        }
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {
            throw new IllegalStateException("当前未找到可用的 EmbeddingModel，无法执行向量化。");
        }
        return embeddingModel;
    }

    private String resolveEmbeddingModelName() {
        return StrUtil.isNotBlank(embeddingModelName) ? embeddingModelName : "default";
    }

    private int defaultInteger(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }
}
