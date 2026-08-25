package com.dochub.workbench.manage.support;

import com.dochub.workbench.manage.config.QdrantProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文枢 DocHub 向量数据库 Qdrant 封装（REST API）。
 *
 * <p>提供 collection 创建、批量 upsert、相似度检索、按过滤条件删除。
 * 集合维度按首次写入的向量长度自适应，避免硬编码维度。</p>
 */
@Slf4j
@Component
public class QdrantVectorStore {

    private final QdrantProperties properties;
    private final RestClient restClient;

    public QdrantVectorStore(QdrantProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
            .baseUrl("http://" + properties.getHost() + ":" + properties.getPort())
            .build();
    }

    /**
     * 确保 collection 存在；不存在则按指定维度（余弦距离）创建。
     */
    public void ensureCollection(String collection, int dimension) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("vectors", Map.of("size", dimension, "distance", "Cosine"));
            restClient.put()
                .uri("/collections/{name}", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            log.info("Qdrant 集合已创建: {} (dim={})", collection, dimension);
        }
        catch (Exception exception) {
            // 集合已存在时会返回 400，忽略
            log.debug("Qdrant 集合创建（可能已存在）: {} - {}", collection, exception.getMessage());
        }
    }

    /**
     * 文档向量集合专用初始化：建集合 + 给文本字段建全文索引（供关键字检索回退用）。
     */
    public void ensureDocumentCollection(int dimension) {
        ensureCollection(documentCollection(), dimension);
        createTextIndex(documentCollection(), "chunk_text");
        createTextIndex(documentCollection(), "section_path");
        createTextIndex(documentCollection(), "canonical_path");
    }

    private void createTextIndex(String collection, String field) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("field_name", field);
            body.put("field_schema", Map.of("type", "text"));
            restClient.put()
                .uri("/collections/{name}/index", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
        }
        catch (Exception exception) {
            // 索引已存在时返回 400，忽略
            log.debug("Qdrant 文本索引创建（可能已存在）: {}.{} - {}", collection, field, exception.getMessage());
        }
    }

    /**
     * 批量写入点（id + 向量 + 元数据 payload）。
     */
    public void upsert(String collection, List<Point> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        ensureCollection(collection, points.get(0).vector().length);
        List<Map<String, Object>> pointMaps = new ArrayList<>();
        for (Point point : points) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", point.id());
            map.put("vector", point.vector());
            if (point.payload() != null && !point.payload().isEmpty()) {
                map.put("payload", point.payload());
            }
            pointMaps.add(map);
        }
        restClient.put()
            .uri("/collections/{name}/points?wait=true", collection)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("points", pointMaps))
            .retrieve()
            .toBodilessEntity();
    }

    /**
     * 相似度检索。filter 为 Qdrant 过滤条件 JSON 结构（可为 null 全量检索）。
     */
    public List<SearchHit> search(String collection, float[] vector, int topK, Map<String, Object> filter) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("vector", vector);
        body.put("limit", Math.max(1, topK));
        body.put("with_payload", true);
        if (filter != null && !filter.isEmpty()) {
            body.put("filter", filter);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                .uri("/collections/{name}/points/search", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
            List<SearchHit> hits = new ArrayList<>();
            Object resultObj = response == null ? null : response.get("result");
            if (resultObj instanceof List<?> resultList) {
                for (Object item : resultList) {
                    if (!(item instanceof Map<?, ?> hitMap)) {
                        continue;
                    }
                    Object id = hitMap.get("id");
                    Object score = hitMap.get("score");
                    Object payload = hitMap.get("payload");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payloadMap = payload instanceof Map<?, ?> m
                        ? (Map<String, Object>) m : Map.of();
                    hits.add(new SearchHit(id == null ? 0L : Long.parseLong(String.valueOf(id)),
                        score instanceof Number n ? n.doubleValue() : 0D, payloadMap));
                }
            }
            return hits;
        }
        catch (Exception exception) {
            log.warn("Qdrant 检索失败: {}", exception.getMessage());
            return List.of();
        }
    }

    /**
     * 按过滤条件删除（如按 documentId 删除整份文档的向量）。
     */
    public void deleteByFilter(String collection, Map<String, Object> filter) {
        if (filter == null || filter.isEmpty()) {
            return;
        }
        try {
            restClient.post()
                .uri("/collections/{name}/points/delete?wait=true", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("filter", filter))
                .retrieve()
                .toBodilessEntity();
        }
        catch (Exception exception) {
            log.warn("Qdrant 删除失败: {}", exception.getMessage());
        }
    }

    public String documentCollection() {
        return properties.getDocumentCollection();
    }

    public String memoryCollection() {
        return properties.getMemoryCollection();
    }

    public record Point(long id, float[] vector, Map<String, Object> payload) {
    }

    public record SearchHit(long id, double score, Map<String, Object> payload) {
    }
}
