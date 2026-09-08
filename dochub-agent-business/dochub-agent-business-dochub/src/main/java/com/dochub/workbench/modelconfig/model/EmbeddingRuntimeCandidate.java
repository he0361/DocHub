package com.dochub.workbench.modelconfig.model;

import org.springframework.ai.embedding.EmbeddingModel;

public record EmbeddingRuntimeCandidate(long configVersion, Long configId, EmbeddingModel model,
                                        ModelRuntimeSpec spec, int dimension,
                                        String documentCollection, String memoryCollection) { }
