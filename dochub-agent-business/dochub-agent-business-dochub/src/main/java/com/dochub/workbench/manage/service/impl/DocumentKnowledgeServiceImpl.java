package com.dochub.workbench.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.manage.config.DocumentManageProperties;
import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.data.DochubDocumentParentBlock;
import com.dochub.workbench.manage.mapper.DochubDocumentMapper;
import com.dochub.workbench.manage.mapper.DochubDocumentParentBlockMapper;
import com.dochub.workbench.manage.model.DocumentRetrieveFilters;
import com.dochub.workbench.manage.model.DocumentRetrieveRequest;
import com.dochub.workbench.manage.model.KnowledgeDocumentDescriptor;
import com.dochub.workbench.manage.service.DocumentKnowledgeService;
import com.dochub.workbench.manage.service.keyword.DocumentKeywordSearchGateway;
import com.dochub.workbench.manage.support.DocumentKnowledgeMetadataKeys;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import org.javaup.enums.BusinessStatus;
import org.javaup.enums.DocumentIndexStatusEnum;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@Slf4j
@AllArgsConstructor
@Service
public class DocumentKnowledgeServiceImpl implements DocumentKnowledgeService {

    private static final Pattern ALNUM_TOKEN_PATTERN = Pattern.compile("[a-z0-9._-]{2,}");

    private static final Pattern CHINESE_TOKEN_PATTERN = Pattern.compile("[\\p{IsHan}]{2,}");

    private static final List<String> CHINESE_NOISE_PHRASES = List.of(
        "请问", "帮我", "一下子", "一下", "如何", "怎么", "什么", "哪个", "这个", "那个", "是否", "关于", "可以", "需要", "想问", "看看"
    );

    private static final Pattern CHINESE_SEGMENT_SPLIT_PATTERN = Pattern.compile("[的和及与或]");

    private static final int MAX_KEYWORD_TERMS = 8;

    private final DochubDocumentMapper documentMapper;
    
    private final DochubDocumentParentBlockMapper parentBlockMapper;
    
    private final QdrantVectorStore qdrantVectorStore;

    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;
    
    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;
    
    private final DocumentManageProperties properties;

    @Override
    public List<KnowledgeDocumentDescriptor> listRetrievableDocuments() {

        List<DochubDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<DochubDocument>()
            .eq(DochubDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(DochubDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(DochubDocument::getLastIndexTaskId)
            .orderByDesc(DochubDocument::getEditTime)
            .orderByDesc(DochubDocument::getId));
        if (CollUtil.isEmpty(documents)) {
            return List.of();
        }

        return documents.stream()
            .map(document -> new KnowledgeDocumentDescriptor(
                document.getId(),
                document.getDocumentName(),
                document.getLastIndexTaskId(),
                document.getKnowledgeScopeCode(),
                document.getKnowledgeScopeName(),
                document.getBusinessCategory(),
                document.getDocumentTags()
            ))
            .toList();
    }

    @Override
    public List<Document> vectorSearch(DocumentRetrieveRequest request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }

        EmbeddingModel embeddingModel = requireEmbeddingModel();

        float[] questionVector = embeddingModel.embed(request.getRetrievalQuery().trim());
        List<Long> documentIds = request.resolvedDocumentIds();

        Map<Long, KnowledgeDocumentDescriptor> descriptorMap = listDescriptorMap(documentIds);

        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }

        Map<String, Object> filter = buildQdrantFilter(resolvedScope);
        List<QdrantVectorStore.SearchHit> hits = qdrantVectorStore.search(
            qdrantVectorStore.documentCollection(),
            questionVector,
            resolveTopK(request.getTopK()),
            filter
        );

        List<Document> documents = new ArrayList<>();
        for (QdrantVectorStore.SearchHit hit : hits) {
            Map<String, Object> payload = hit.payload();
            long documentId = longValue(payload.get("document_id"));
            KnowledgeDocumentDescriptor descriptor = descriptorMap.get(documentId);
            documents.add(buildRetrievedDocument(
                hit.id(),
                stringValue(payload.get("chunk_text")),
                longValue(payload.get("task_id")),
                longValue(payload.get("parent_block_id")),
                intValue(payload.get("chunk_no")),
                stringValue(payload.get("section_path")),
                longOrNull(payload.get("structure_node_id")),
                intOrNull(payload.get("structure_node_type")),
                stringValue(payload.get("canonical_path")),
                intOrNull(payload.get("item_index")),
                descriptor,
                "vector",
                hit.score()
            ));
        }
        return documents;
    }

    /** 把检索范围翻译成 Qdrant 过滤条件（document/task 范围 + 结构导航）。 */
    private Map<String, Object> buildQdrantFilter(ResolvedMetadataScope scope) {
        List<Object> must = new ArrayList<>();
        if (CollUtil.isNotEmpty(scope.documentIds())) {
            must.add(Map.of("key", "document_id", "match_any", Map.of("any", scope.documentIds())));
        }
        if (CollUtil.isNotEmpty(scope.taskIds())) {
            must.add(Map.of("key", "task_id", "match_any", Map.of("any", scope.taskIds())));
        }
        DocumentRetrieveFilters filters = scope.filters();
        if (filters != null) {
            if (CollUtil.isNotEmpty(filters.getStructureNodeIdHints())) {
                must.add(Map.of("key", "structure_node_id", "match_any", Map.of("any", filters.getStructureNodeIdHints())));
            }
            if (CollUtil.isNotEmpty(filters.getItemIndexHints())) {
                must.add(Map.of("key", "item_index", "match_any", Map.of("any", filters.getItemIndexHints())));
            }
            if (CollUtil.isNotEmpty(filters.getSectionPathHints())) {
                for (String hint : filters.getSectionPathHints()) {
                    must.add(Map.of("key", "section_path", "match_text", hint));
                }
            }
            if (CollUtil.isNotEmpty(filters.getCanonicalPathHints())) {
                for (String hint : filters.getCanonicalPathHints()) {
                    must.add(Map.of("key", "canonical_path", "match_text", hint));
                }
            }
        }
        return must.isEmpty() ? null : Map.of("must", must);
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long longOrNull(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private int intValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private Integer intOrNull(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    @Override
    public List<Document> keywordSearch(DocumentRetrieveRequest request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }

        List<Long> documentIds = request.resolvedDocumentIds();
        List<Long> taskIds = request.resolvedTaskIds();
        Map<Long, KnowledgeDocumentDescriptor> descriptorMap = listDescriptorMap(documentIds);
        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }

        DocumentRetrieveRequest filteredRequest = new DocumentRetrieveRequest(
            request.getQuestion(),
            request.getRetrievalQuery(),
            resolvedScope.documentIds().isEmpty() ? null : resolvedScope.documentIds().get(0),
            resolvedScope.taskIds().isEmpty() ? null : resolvedScope.taskIds().get(0),
            request.getTopK(),
            resolvedScope.filters(),
            request.getQueryContextHints()
        );
        filteredRequest.setDocumentIds(resolvedScope.documentIds());
        filteredRequest.setTaskIds(resolvedScope.taskIds());

        DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
        if (Boolean.TRUE.equals(properties.getElasticsearch().getEnabled()) && keywordSearchGateway != null) {
            return keywordSearchGateway.search(filteredRequest);
        }

        return keywordSearchByQdrant(request, resolvedScope, descriptorMap);
    }

    /** ES 关闭时：关键字检索回退到 Qdrant（全文索引过滤 + 向量排序）。 */
    private List<Document> keywordSearchByQdrant(DocumentRetrieveRequest request,
                                                 ResolvedMetadataScope scope,
                                                 Map<Long, KnowledgeDocumentDescriptor> descriptorMap) {
        List<String> terms = new ArrayList<>(extractKeywordTerms(request.getRetrievalQuery()));
        terms.addAll(extractAuxiliaryKeywordTerms(request.getQueryContextHints()));
        terms = new ArrayList<>(new LinkedHashSet<>(terms));
        if (terms.isEmpty()) {
            return List.of();
        }
        EmbeddingModel embeddingModel = requireEmbeddingModel();
        float[] queryVector = embeddingModel.embed(request.getRetrievalQuery().trim());
        Map<String, Object> filter = buildKeywordQdrantFilter(scope, terms);
        List<QdrantVectorStore.SearchHit> hits = qdrantVectorStore.search(
            qdrantVectorStore.documentCollection(),
            queryVector,
            resolveTopK(request.getTopK()),
            filter
        );
        List<Document> documents = new ArrayList<>();
        for (QdrantVectorStore.SearchHit hit : hits) {
            Map<String, Object> payload = hit.payload();
            long documentId = longValue(payload.get("document_id"));
            KnowledgeDocumentDescriptor descriptor = descriptorMap.get(documentId);
            documents.add(buildRetrievedDocument(
                hit.id(),
                stringValue(payload.get("chunk_text")),
                longValue(payload.get("task_id")),
                longValue(payload.get("parent_block_id")),
                intValue(payload.get("chunk_no")),
                stringValue(payload.get("section_path")),
                longOrNull(payload.get("structure_node_id")),
                intOrNull(payload.get("structure_node_type")),
                stringValue(payload.get("canonical_path")),
                intOrNull(payload.get("item_index")),
                descriptor,
                "keyword",
                hit.score()
            ));
        }
        return documents;
    }

    /** 关键字检索的 Qdrant 过滤：文档范围 + 任一 term 命中 chunk_text / section_path。 */
    private Map<String, Object> buildKeywordQdrantFilter(ResolvedMetadataScope scope, List<String> terms) {
        List<Object> must = new ArrayList<>();
        if (CollUtil.isNotEmpty(scope.documentIds())) {
            must.add(Map.of("key", "document_id", "match_any", Map.of("any", scope.documentIds())));
        }
        if (CollUtil.isNotEmpty(scope.taskIds())) {
            must.add(Map.of("key", "task_id", "match_any", Map.of("any", scope.taskIds())));
        }
        List<Object> should = new ArrayList<>();
        for (String term : terms) {
            if (StrUtil.isBlank(term)) {
                continue;
            }
            should.add(Map.of("key", "chunk_text", "match", Map.of("text", term)));
            should.add(Map.of("key", "section_path", "match", Map.of("text", term)));
        }
        if (!should.isEmpty()) {
            must.add(Map.of("should", should));
        }
        return must.isEmpty() ? null : Map.of("must", must);
    }

    @Override
    public List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars) {
        if (CollUtil.isEmpty(childDocuments)) {
            return List.of();
        }

        Map<Long, List<Document>> childGroupsByParent = new LinkedHashMap<>();
        List<Document> fallbackDocuments = new ArrayList<>();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            Long parentBlockId = asLong(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID));
            if (parentBlockId == null) {
                fallbackDocuments.add(childDocument);
                continue;
            }
            childGroupsByParent.computeIfAbsent(parentBlockId, ignored -> new ArrayList<>()).add(childDocument);
        }

        if (childGroupsByParent.isEmpty()) {
            return fallbackDocuments;
        }

        List<Long> parentBlockIds = new ArrayList<>(childGroupsByParent.keySet());
        Map<Long, DochubDocumentParentBlock> parentBlockMap = parentBlockMapper.selectList(
                new LambdaQueryWrapper<DochubDocumentParentBlock>()
                    .in(DochubDocumentParentBlock::getId, parentBlockIds)
                    .eq(DochubDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                    .orderByAsc(DochubDocumentParentBlock::getParentNo)
            ).stream()
            .collect(Collectors.toMap(
                DochubDocumentParentBlock::getId,
                parent -> parent,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<Document> elevatedDocuments = new ArrayList<>(childGroupsByParent.size() + fallbackDocuments.size());
        for (Map.Entry<Long, List<Document>> entry : childGroupsByParent.entrySet()) {
            DochubDocumentParentBlock parentBlock = parentBlockMap.get(entry.getKey());
            if (parentBlock == null) {
                elevatedDocuments.addAll(entry.getValue());
                continue;
            }
            elevatedDocuments.add(buildParentEvidenceDocument(parentBlock, entry.getValue(), maxChars));
        }
        elevatedDocuments.addAll(fallbackDocuments);
        elevatedDocuments.sort(this::compareEvidenceDocument);
        return elevatedDocuments;
    }

    private Document buildRetrievedDocument(long chunkId,
                                            String chunkText,
                                            long taskId,
                                            long parentBlockId,
                                            int chunkNo,
                                            String sectionPath,
                                            Long structureNodeId,
                                            Integer structureNodeType,
                                            String canonicalPath,
                                            Integer itemIndex,
                                            KnowledgeDocumentDescriptor descriptor,
                                            String channel,
                                            double score) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "DOCUMENT");
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, channel);
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, chunkId);
        metadata.put(DocumentKnowledgeMetadataKeys.TASK_ID, taskId);
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlockId);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_NO, chunkNo);
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(sectionPath));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, structureNodeId);
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, structureNodeType);
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(canonicalPath));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, itemIndex);
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, chunkText);
        if (descriptor != null) {

            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, descriptor.getDocumentId());
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, safeText(descriptor.getDocumentName()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_CODE, safeText(descriptor.getKnowledgeScopeCode()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_NAME, safeText(descriptor.getKnowledgeScopeName()));
            metadata.put(DocumentKnowledgeMetadataKeys.BUSINESS_CATEGORY, safeText(descriptor.getBusinessCategory()));
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_TAGS, safeText(descriptor.getDocumentTags()));
        }

        return Document.builder()
            .id(String.valueOf(chunkId))
            .text(chunkText)
            .metadata(metadata)
            .score(score)
            .build();
    }

    private boolean isSearchableRequest(DocumentRetrieveRequest request) {

        if (request == null || StrUtil.isBlank(request.getQuestion()) || StrUtil.isBlank(request.getRetrievalQuery())) {
            return false;
        }
        return !request.resolvedDocumentIds().isEmpty() && !request.resolvedTaskIds().isEmpty();
    }

    private Map<Long, KnowledgeDocumentDescriptor> listDescriptorMap(List<Long> requestedDocumentIds) {
        List<KnowledgeDocumentDescriptor> descriptors = listRetrievableDocuments();
        if (descriptors.isEmpty()) {
            return Map.of();
        }

        return descriptors.stream()
            .filter(descriptor -> requestedDocumentIds.contains(descriptor.getDocumentId()))
            .collect(Collectors.toMap(
                KnowledgeDocumentDescriptor::getDocumentId,
                descriptor -> descriptor,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private ResolvedMetadataScope resolveMetadataScope(DocumentRetrieveRequest request) {
        List<Long> baseDocumentIds = request.resolvedDocumentIds();
        List<Long> baseTaskIds = request.resolvedTaskIds();
        return new ResolvedMetadataScope(baseDocumentIds, baseTaskIds, request.getFilters());
    }

    private Document buildParentEvidenceDocument(DochubDocumentParentBlock parentBlock,
                                                 List<Document> childDocuments,
                                                 int maxChars) {
        Document bestChild = childDocuments.stream()
            .max(Comparator.comparingDouble(document -> {
                Double score = resolveScore(document);
                return score == null ? 0D : score;
            }))
            .orElseThrow();

        double parentScore = aggregateParentScore(childDocuments);
        Map<String, Object> metadata = new LinkedHashMap<>(bestChild.getMetadata());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlock.getId());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO, parentBlock.getParentNo());
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(parentBlock.getSectionPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, parentBlock.getStructureNodeId());
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, parentBlock.getStructureNodeType());
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(parentBlock.getCanonicalPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, parentBlock.getItemIndex());
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, parentScore);
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, safeText(parentBlock.getParentText()));

        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL,
            channels.size() > 1 ? "hybrid" : channels.stream().findFirst().orElse("vector"));

        return Document.builder()
            .id("parent-" + parentBlock.getId())
            .text(renderParentEvidenceText(parentBlock, childDocuments, maxChars))
            .metadata(metadata)
            .score(parentScore)
            .build();
    }

    private double aggregateParentScore(List<Document> childDocuments) {
        double bestChildScore = childDocuments.stream()
            .map(this::resolveScore)
            .filter(Objects::nonNull)
            .max(Double::compareTo)
            .orElse(0D);
        int supportCount = Math.max(0, childDocuments.size() - 1);
        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        double supportWeight = Math.min(0.36D, supportCount * 0.12D);
        double multiChannelWeight = channels.size() > 1 ? 0.10D : 0D;
        return bestChildScore * (1D + supportWeight + multiChannelWeight);
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private int compareEvidenceDocument(Document left, Document right) {
        int scoreCompare = Double.compare(resolveScoreOrZero(right), resolveScoreOrZero(left));
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        Integer leftParentNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        Integer rightParentNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        int parentNoCompare = compareNullableInteger(leftParentNo, rightParentNo);
        if (parentNoCompare != 0) {
            return parentNoCompare;
        }
        Integer leftChunkNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        Integer rightChunkNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        return compareNullableInteger(leftChunkNo, rightChunkNo);
    }

    private double resolveScoreOrZero(Document document) {
        Double score = resolveScore(document);
        return score == null ? 0D : score;
    }

    private int compareNullableInteger(Integer left, Integer right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left, right);
    }

    private String renderParentEvidenceText(DochubDocumentParentBlock parentBlock,
                                            List<Document> childDocuments,
                                            int maxChars) {
        String parentText = safeText(parentBlock.getParentText());
        if (StrUtil.isBlank(parentText)) {
            return childDocuments.isEmpty() ? "" : StrUtil.blankToDefault(childDocuments.get(0).getText(), "");
        }

        StringBuilder hitSummaryBuilder = new StringBuilder();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            if (!hitSummaryBuilder.isEmpty()) {
                hitSummaryBuilder.append('\n');
            }
            hitSummaryBuilder.append("- child#")
                .append(asInteger(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO)))
                .append("：")
                .append(trimText(safeText(childDocument.getText()), 140));
        }

        String composed = joinSections(
            "[父块内容]\n" + parentText,
            hitSummaryBuilder.isEmpty() ? "" : "[命中子片段]\n" + hitSummaryBuilder
        );
        return trimText(composed, Math.max(1, maxChars));
    }

    private Double resolveScore(Document document) {
        if (document == null) {
            return null;
        }
        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        if (metadataScore instanceof Number number) {
            return number.doubleValue();
        }
        return document.getScore();
    }

    private String joinSections(String... sections) {
        List<String> parts = new ArrayList<>();
        for (String section : sections) {
            if (StrUtil.isNotBlank(section)) {
                parts.add(section.trim());
            }
        }
        return String.join("\n\n", parts);
    }

    private String trimText(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private List<String> extractKeywordTerms(String question) {
        String normalized = normalizeQuestion(question);
        if (StrUtil.isBlank(normalized)) {
            return List.of();
        }

        LinkedHashSet<String> terms = new LinkedHashSet<>();

        Matcher alnumMatcher = ALNUM_TOKEN_PATTERN.matcher(normalized);
        while (alnumMatcher.find()) {
            terms.add(alnumMatcher.group());
        }

        Matcher chineseMatcher = CHINESE_TOKEN_PATTERN.matcher(normalized);
        while (chineseMatcher.find()) {
            for (String segment : splitChineseSegments(chineseMatcher.group())) {
                addChineseSegmentTerms(segment, terms);
                if (terms.size() >= MAX_KEYWORD_TERMS * 2) {
                    break;
                }
            }
            if (terms.size() >= MAX_KEYWORD_TERMS * 2) {
                break;
            }
        }

        return terms.stream()
            .filter(term -> term.length() >= 2)

            .limit(MAX_KEYWORD_TERMS)
            .toList();
    }

    private List<String> splitChineseSegments(String chineseToken) {
        String cleanedToken = removeChineseNoisePhrases(chineseToken);
        if (cleanedToken.length() < 2) {
            return List.of();
        }
        LinkedHashSet<String> segments = new LinkedHashSet<>();
        segments.add(cleanedToken);
        for (String segment : CHINESE_SEGMENT_SPLIT_PATTERN.split(cleanedToken)) {
            String normalizedSegment = segment == null ? "" : segment.trim();
            if (normalizedSegment.length() >= 2) {
                segments.add(normalizedSegment);
            }
        }
        return new ArrayList<>(segments);
    }

    private List<String> extractAuxiliaryKeywordTerms(List<String> hints) {
        if (CollUtil.isEmpty(hints)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String hint : hints) {
            if (StrUtil.isBlank(hint)) {
                continue;
            }
            terms.addAll(extractKeywordTerms(hint));
            if (terms.size() >= MAX_KEYWORD_TERMS) {
                break;
            }
        }
        return new ArrayList<>(terms);
    }

    private void addChineseSegmentTerms(String segment, LinkedHashSet<String> terms) {
        if (StrUtil.isBlank(segment) || segment.length() < 2) {
            return;
        }

        if (segment.length() <= 12) {
            terms.add(segment);
        }
        addTailNgrams(segment, terms);
        addHeadNgrams(segment, terms);
        addSlidingNgrams(segment, terms);
    }

    private String normalizeQuestion(String question) {
        if (StrUtil.isBlank(question)) {
            return "";
        }

        return question.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\r\\n\\t]+", " ")
            .replaceAll("\\s+", " ");
    }

    private String removeChineseNoisePhrases(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }

        String normalized = text.trim();
        for (String phrase : CHINESE_NOISE_PHRASES) {
            normalized = normalized.replace(phrase, "");
        }
        return normalized.trim();
    }

    private void addTailNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            terms.add(segment.substring(segment.length() - size));
        }
    }

    private void addHeadNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            terms.add(segment.substring(0, size));
        }
    }

    private void addSlidingNgrams(String segment, LinkedHashSet<String> terms) {
        int maxGram = Math.min(4, segment.length());
        for (int size = maxGram; size >= 2 && terms.size() < MAX_KEYWORD_TERMS * 2; size--) {
            for (int index = 0; index <= segment.length() - size && terms.size() < MAX_KEYWORD_TERMS * 2; index++) {
                terms.add(segment.substring(index, index + size));
            }
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int resolveTopK(int topK) {

        return topK <= 0 ? 10 : Math.min(topK, 50);
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel == null) {

            throw new IllegalStateException("当前未找到可用的 EmbeddingModel，无法执行向量检索。");
        }
        return embeddingModel;
    }

    private int defaultInteger(Integer value) {
        return Objects.requireNonNullElse(value, 0);
    }

    private record ResolvedMetadataScope(
        List<Long> documentIds,
        List<Long> taskIds,
        DocumentRetrieveFilters filters
    ) {
    }
}
