package com.dochub.workbench.modelconfig.model;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Persisted fields required to reconstruct one complete embedding runtime snapshot. */
public record EmbeddingRuntimeMetadata(int dimension, String documentCollection, String memoryCollection) {
    public String toJson(ObjectMapper objectMapper) {
        try { return objectMapper.writeValueAsString(this); }
        catch (Exception exception) { throw new IllegalStateException("序列化向量运行时元数据失败", exception); }
    }

    public static EmbeddingRuntimeMetadata fromJson(ObjectMapper objectMapper, String json) {
        try {
            EmbeddingRuntimeMetadata value = objectMapper.readValue(json, EmbeddingRuntimeMetadata.class);
            if (value.dimension <= 0 || blank(value.documentCollection) || blank(value.memoryCollection)) {
                throw new IllegalArgumentException("元数据字段不完整");
            }
            return value;
        } catch (Exception exception) {
            throw new IllegalStateException("向量运行时元数据无效", exception);
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
