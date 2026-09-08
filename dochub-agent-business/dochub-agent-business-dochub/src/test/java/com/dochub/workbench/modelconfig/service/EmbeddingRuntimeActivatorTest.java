package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EmbeddingRuntimeActivatorTest {
    @AfterEach void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void runtimeChangesOnlyAfterDatabaseTransactionCommits() {
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        EmbeddingModel oldModel = mock(EmbeddingModel.class);
        EmbeddingModel newModel = mock(EmbeddingModel.class);
        registry.activateEmbedding(new EmbeddingRuntimeSnapshot(7, oldModel, spec("a"), 3, "document_v7", "memory_v7"));
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(8L); job.setTargetConfigVersion(8L); job.setTargetDimension(3);
        job.setTargetDocumentCollection("document_v8"); job.setTargetMemoryCollection("memory_v8");
        DochubAiModelConfig config = new DochubAiModelConfig(); config.setId(88L); config.setConfigVersion(8L);
        EmbeddingRuntimeSnapshot target = new EmbeddingRuntimeSnapshot(8, newModel, spec("b"), 3, "document_v8", "memory_v8");
        EmbeddingRuntimeActivator activator = new EmbeddingRuntimeActivator(mock(DochubAiModelConfigMapper.class),
            mock(DochubEmbeddingModelMigrationMapper.class), mock(DochubAiModelConfigAuditMapper.class),
            mock(com.baidu.fsg.uid.UidGenerator.class), registry, mock(ModelConfigVersionPublisher.class));

        TransactionSynchronizationManager.initSynchronization();
        activator.activate(job, config, target, 1L, "ACTIVATE_EMBEDDING");
        assertThat(registry.captureEmbedding().configVersion()).isEqualTo(7L);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) synchronization.afterCommit();
        assertThat(registry.captureEmbedding().configVersion()).isEqualTo(8L);
        assertThat(registry.captureEmbedding().documentCollection()).isEqualTo("document_v8");
        assertThat(registry.captureEmbedding().memoryCollection()).isEqualTo("memory_v8");
    }

    private ModelRuntimeSpec spec(String modelName) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", modelName, null, null, 30_000);
    }
}
