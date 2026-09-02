package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.classify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
public class KnowledgeRouteSemanticCandidateService {
    private final ObjectProvider<EmbeddingModel> embeddingModelProvider;

    public KnowledgeRouteSemanticCandidateService(ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        this.embeddingModelProvider = embeddingModelProvider;
    }

    public SemanticCandidates findScopes(ClassificationMaterial material, List<RouteDescriptor> routes, int limit) {
        EmbeddingModel model = embeddingModelProvider.getIfAvailable();
        if (model == null) return SemanticCandidates.unavailable("embedding_model_unavailable");
        try {
            float[] query = model.embed(material.titleAndSummary() + " " + material.semanticText());
            List<float[]> vectors = model.embed(routes.stream().map(RouteDescriptor::searchableText).toList());
            if (vectors == null || vectors.size() != routes.size()) return SemanticCandidates.unavailable("embedding_count_mismatch");
            List<RouteCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < routes.size(); i++) {
                double score = cosine(query, vectors.get(i));
                candidates.add(new RouteCandidate(routes.get(i), 0, score, score, "语义相似度 " + String.format(Locale.ROOT, "%.2f", score)));
            }
            candidates.sort(Comparator.comparingDouble(RouteCandidate::semanticScore).reversed());
            return new SemanticCandidates(candidates.stream().limit(limit).toList(), true, "");
        } catch (Exception exception) {
            log.warn("知识分类语义候选不可用，将仅使用确定性证据: {}", exception.getMessage());
            return SemanticCandidates.unavailable(exception.getClass().getSimpleName());
        }
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length == 0 || left.length != right.length) return 0;
        double dot = 0, a = 0, b = 0;
        for (int i = 0; i < left.length; i++) { dot += left[i] * right[i]; a += left[i] * left[i]; b += right[i] * right[i]; }
        return a == 0 || b == 0 ? 0 : Math.max(0, Math.min(1, dot / (Math.sqrt(a) * Math.sqrt(b))));
    }
}
