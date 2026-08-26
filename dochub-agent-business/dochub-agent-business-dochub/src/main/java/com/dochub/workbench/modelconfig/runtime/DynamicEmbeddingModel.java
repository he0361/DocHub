package com.dochub.workbench.modelconfig.runtime;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.Objects;

/** A primary embedding model that delegates each invocation to the currently active snapshot. */
public final class DynamicEmbeddingModel implements EmbeddingModel {

    private final ModelRuntimeRegistry registry;

    public DynamicEmbeddingModel(ModelRuntimeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return registry.requireEmbedding().model().call(request);
    }

    @Override
    public float[] embed(Document document) {
        return registry.requireEmbedding().model().embed(document);
    }

    @Override
    public int dimensions() {
        return registry.requireEmbedding().model().dimensions();
    }
}
