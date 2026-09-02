package com.dochub.workbench.modelconfig.service;

public interface EmbeddingCollectionRebuildWorker {
    void resume(Long migrationId);
}
