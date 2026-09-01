package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Objects;

/** One indivisible embedding runtime: model and the collections created with it. */
public record EmbeddingRuntimeSnapshot(long configVersion,
                                       EmbeddingModel model,
                                       ModelRuntimeSpec spec,
                                       int dimension,
                                       String documentCollection,
                                       String memoryCollection) {
    public EmbeddingRuntimeSnapshot {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
        if (dimension < 0) throw new IllegalArgumentException("dimension must not be negative");
        if (documentCollection == null || documentCollection.isBlank()) throw new IllegalArgumentException("documentCollection must not be blank");
        if (memoryCollection == null || memoryCollection.isBlank()) throw new IllegalArgumentException("memoryCollection must not be blank");
    }
}
