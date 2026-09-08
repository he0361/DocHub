package com.dochub.workbench.modelconfig.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.EmbeddingMigrationStatus;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.service.impl.EmbeddingMigrationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingMigrationServiceTest {

    @Test
    void lifecycleAllowsOnlyForwardOrRetryableFailureTransitions() {
        assertThat(EmbeddingMigrationStatus.PENDING.canTransitionTo(EmbeddingMigrationStatus.REBUILDING_DOCUMENTS)).isTrue();
        assertThat(EmbeddingMigrationStatus.REBUILDING_DOCUMENTS.canTransitionTo(EmbeddingMigrationStatus.FAILED)).isTrue();
        assertThat(EmbeddingMigrationStatus.COMPLETED.canTransitionTo(EmbeddingMigrationStatus.SWITCHING)).isFalse();
    }

    @Test
    void transitionGuardRejectsSkippingDirectlyToSwitching() {
        assertThatThrownBy(() -> EmbeddingMigrationStatus.PENDING.requireTransition(EmbeddingMigrationStatus.SWITCHING))
            .hasMessageContaining("非法");
    }

    @Test
    void finalizingMigrationAtomicallyRejectsANewWriter() {
        DochubEmbeddingModelMigrationMapper mapper = mock(DochubEmbeddingModelMigrationMapper.class);
        DochubEmbeddingModelMigration job = job(8L, EmbeddingMigrationStatus.FINALIZING);
        when(mapper.findMutationTarget()).thenReturn(job);
        when(mapper.selectById(8L)).thenReturn(job);
        EmbeddingMigrationServiceImpl service = service(mapper);

        assertThatThrownBy(service::beginMutation).hasMessageContaining("安全切换");
    }

    @Test
    void retryIsRejectedWhileAnotherMigrationIsActive() {
        DochubEmbeddingModelMigrationMapper mapper = mock(DochubEmbeddingModelMigrationMapper.class);
        when(mapper.countActive()).thenReturn(1L);
        EmbeddingMigrationServiceImpl service = service(mapper);

        assertThatThrownBy(() -> service.retry(8L)).hasMessageContaining("已有向量模型重建任务");
        verify(mapper).lockMigrationSlot();
    }

    private EmbeddingMigrationServiceImpl service(DochubEmbeddingModelMigrationMapper mapper) {
        return new EmbeddingMigrationServiceImpl(mapper, mock(UidGenerator.class), new ModelRuntimeRegistry(),
            mock(ObjectProvider.class), mock(TaskExecutor.class));
    }

    private DochubEmbeddingModelMigration job(long id, EmbeddingMigrationStatus status) {
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(id); job.setMigrationStatus(status.name());
        return job;
    }
}
