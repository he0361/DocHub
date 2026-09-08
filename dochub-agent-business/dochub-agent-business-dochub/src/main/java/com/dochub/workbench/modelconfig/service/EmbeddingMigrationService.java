package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.model.EmbeddingRuntimeCandidate;

public interface EmbeddingMigrationService {
    DochubEmbeddingModelMigration start(EmbeddingRuntimeCandidate candidate, Long operator);
    DochubEmbeddingModelMigration current();
    DochubEmbeddingModelMigration find(Long migrationId);
    void schedule(Long migrationId);
    void retry(Long migrationId);
    boolean finalizing();
    Long beginMutation();
    void finishMutation(Long migrationId);
}
