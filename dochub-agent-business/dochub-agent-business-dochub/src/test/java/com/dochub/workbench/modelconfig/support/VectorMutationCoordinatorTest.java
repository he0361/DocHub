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
        when(migrations.current()).thenReturn(job); when(ids.getUid()).thenReturn(101L);
        VectorMutationCoordinator coordinator = new VectorMutationCoordinator(migrations, mapper, ids);
        DochubDocumentChunk chunk = new DochubDocumentChunk(); chunk.setId(55L);

        coordinator.documentUpsert(List.of(chunk));

        verify(mapper).insert(org.mockito.ArgumentMatchers.<DochubEmbeddingMigrationDelta>argThat(delta -> delta.getMigrationId().equals(8L)
            && delta.getResourceId().equals(55L) && "UPSERT".equals(delta.getOperation())));
    }

    @Test
    void finalizationBarrierRejectsNewMutations() {
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        when(migrations.finalizing()).thenReturn(true);
        VectorMutationCoordinator coordinator = new VectorMutationCoordinator(migrations, mock(DochubEmbeddingMigrationDeltaMapper.class), mock(UidGenerator.class));
        assertThatThrownBy(coordinator::assertMutationAllowed).hasMessageContaining("安全切换");
    }
}
