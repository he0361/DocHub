package com.dochub.workbench.modelconfig.service.impl;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.EmbeddingMigrationStatus;
import com.dochub.workbench.modelconfig.model.EmbeddingRuntimeCandidate;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.service.EmbeddingCollectionRebuildWorker;
import com.dochub.workbench.modelconfig.service.EmbeddingMigrationService;
import org.javaup.exception.DochubFrameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;

@Service
public class EmbeddingMigrationServiceImpl implements EmbeddingMigrationService {
    private final DochubEmbeddingModelMigrationMapper mapper;
    private final UidGenerator uidGenerator;
    private final ModelRuntimeRegistry registry;
    private final ObjectProvider<EmbeddingCollectionRebuildWorker> workerProvider;
    private final TaskExecutor taskExecutor;

    public EmbeddingMigrationServiceImpl(DochubEmbeddingModelMigrationMapper mapper, UidGenerator uidGenerator,
                                         ModelRuntimeRegistry registry,
                                         ObjectProvider<EmbeddingCollectionRebuildWorker> workerProvider,
                                         TaskExecutor taskExecutor) {
        this.mapper = mapper; this.uidGenerator = uidGenerator; this.registry = registry;
        this.workerProvider = workerProvider; this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DochubEmbeddingModelMigration start(EmbeddingRuntimeCandidate candidate, Long operator) {
        mapper.lockMigrationSlot();
        if (mapper.countActive() > 0) throw new DochubFrameException(409, "已有向量模型重建任务正在运行");
        EmbeddingRuntimeSnapshot source = registry.findEmbedding().orElse(null);
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(uidGenerator.getUid());
        job.setSourceConfigVersion(source == null ? 0L : source.configVersion()); job.setTargetConfigVersion(candidate.configVersion());
        job.setSourceModelName(source == null ? "<unconfigured>" : source.spec().modelName()); job.setTargetModelName(candidate.spec().modelName());
        job.setSourceDimension(source == null ? 0 : source.dimension()); job.setTargetDimension(candidate.dimension());
        job.setSourceDocumentCollection(source == null ? "dochub_document" : source.documentCollection()); job.setTargetDocumentCollection(candidate.documentCollection());
        job.setSourceMemoryCollection(source == null ? "dochub_memory" : source.memoryCollection()); job.setTargetMemoryCollection(candidate.memoryCollection());
        job.setMigrationStatus(EmbeddingMigrationStatus.PENDING.name());
        job.setDocumentTotal(0L); job.setDocumentProcessed(0L); job.setDocumentFailed(0L);
        job.setMemoryTotal(0L); job.setMemoryProcessed(0L); job.setMemoryFailed(0L);
        job.setLastDocumentChunkId(0L); job.setLastMemorySummaryId(0L); job.setLastDeltaSequence(0L);
        job.setActiveMutations(0);
        job.setOperator(operator); job.setLockVersion(0); job.setStartTime(new Date()); job.setCreateTime(new Date()); job.setEditTime(new Date()); job.setStatus(1);
        mapper.insert(job);
        return job;
    }

    @Override public DochubEmbeddingModelMigration current() { return mapper.findActive(); }
    @Override public DochubEmbeddingModelMigration find(Long migrationId) { return mapper.selectById(migrationId); }

    @Override public void schedule(Long migrationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
            && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { execute(migrationId); }
            });
            return;
        }
        execute(migrationId);
    }

    private void execute(Long migrationId) {
        taskExecutor.execute(() -> {
            EmbeddingCollectionRebuildWorker worker = workerProvider.getIfAvailable();
            if (worker != null) worker.resume(migrationId);
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long migrationId) {
        mapper.lockMigrationSlot();
        if (mapper.countActive() > 0) throw new DochubFrameException(409, "已有向量模型重建任务正在运行");
        DochubEmbeddingModelMigration job = mapper.selectById(migrationId);
        if (job == null || !EmbeddingMigrationStatus.FAILED.name().equals(job.getMigrationStatus())) {
            throw new DochubFrameException(409, "仅失败的向量迁移任务可以重试");
        }
        job.setMigrationStatus(resolveResumeStatus(job).name()); job.setResumeStatus(null); job.setErrorSummary(null);
        job.setFinishTime(null); job.setEditTime(new Date());
        mapper.updateById(job); schedule(migrationId);
    }

    @Override public boolean finalizing() {
        DochubEmbeddingModelMigration job = current();
        return job != null && (EmbeddingMigrationStatus.FINALIZING.name().equals(job.getMigrationStatus())
            || EmbeddingMigrationStatus.VERIFYING.name().equals(job.getMigrationStatus())
            || EmbeddingMigrationStatus.SWITCHING.name().equals(job.getMigrationStatus()));
    }

    @Override
    public Long beginMutation() {
        DochubEmbeddingModelMigration job = mapper.findMutationTarget();
        if (job == null) return null;
        if (mapper.beginMutation(job.getId()) == 1) return job.getId();
        DochubEmbeddingModelMigration latest = mapper.selectById(job.getId());
        if (latest != null && status(latest) != EmbeddingMigrationStatus.COMPLETED) {
            throw new DochubFrameException(409, "向量模型正在完成安全切换，请稍后重试");
        }
        return null;
    }

    @Override
    public void finishMutation(Long migrationId) {
        if (migrationId != null) mapper.finishMutation(migrationId);
    }

    private EmbeddingMigrationStatus status(DochubEmbeddingModelMigration job) {
        return EmbeddingMigrationStatus.valueOf(job.getMigrationStatus());
    }

    private EmbeddingMigrationStatus resolveResumeStatus(DochubEmbeddingModelMigration job) {
        if (job.getResumeStatus() != null && !job.getResumeStatus().isBlank()) {
            return EmbeddingMigrationStatus.valueOf(job.getResumeStatus());
        }
        if (job.getLastDocumentChunkId() == null || job.getLastDocumentChunkId() == 0) return EmbeddingMigrationStatus.PENDING;
        if (job.getLastMemorySummaryId() == null || job.getLastMemorySummaryId() == 0) return EmbeddingMigrationStatus.REBUILDING_DOCUMENTS;
        return EmbeddingMigrationStatus.CATCHING_UP;
    }
}
