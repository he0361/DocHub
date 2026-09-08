package com.dochub.workbench.modelconfig.support;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingMigrationDelta;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import com.dochub.workbench.modelconfig.service.EmbeddingMigrationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorMutationCoordinatorTest {
    @Test
    void journalsActiveDocumentMutationForCatchUp() {
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        DochubEmbeddingMigrationDeltaMapper mapper = mock(DochubEmbeddingMigrationDeltaMapper.class);
        UidGenerator ids = mock(UidGenerator.class);
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration(); job.setId(8L); job.setMigrationStatus("REBUILDING_DOCUMENTS");
        when(migrations.beginMutation()).thenReturn(8L); when(ids.getUid()).thenReturn(101L);
        VectorMutationCoordinator coordinator = new VectorMutationCoordinator(migrations, mapper, ids);
        DochubDocumentChunk chunk = new DochubDocumentChunk(); chunk.setId(55L);

        try (VectorMutationCoordinator.MutationPermit permit = coordinator.beginMutation()) {
            coordinator.documentUpsert(permit, List.of(chunk));
        }

        verify(mapper).insert(org.mockito.ArgumentMatchers.<DochubEmbeddingMigrationDelta>argThat(delta -> delta.getMigrationId().equals(8L)
            && delta.getResourceId().equals(55L) && "DOCUMENT_CHUNK".equals(delta.getResourceType())
            && "UPSERT".equals(delta.getOperation())));
        verify(migrations).finishMutation(8L);
    }

    @Test
    void finalizationBarrierRejectsNewMutations() {
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        when(migrations.beginMutation()).thenThrow(new org.javaup.exception.DochubFrameException(409,
            "向量模型正在完成安全切换，请稍后重试"));
        VectorMutationCoordinator coordinator = new VectorMutationCoordinator(migrations, mock(DochubEmbeddingMigrationDeltaMapper.class), mock(UidGenerator.class));
        assertThatThrownBy(coordinator::beginMutation).hasMessageContaining("安全切换");
    }
}
