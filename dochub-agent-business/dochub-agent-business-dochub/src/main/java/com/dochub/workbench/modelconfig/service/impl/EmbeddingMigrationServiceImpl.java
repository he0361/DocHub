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
import com.dochub.workbench.modelconfig.support.EmbeddingMigrationLease;
import org.javaup.exception.DochubFrameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
public class EmbeddingMigrationServiceImpl implements EmbeddingMigrationService {
    private final DochubEmbeddingModelMigrationMapper mapper;
    private final UidGenerator uidGenerator;
    private final ModelRuntimeRegistry registry;
    private final EmbeddingMigrationLease lease;
    private final ObjectProvider<EmbeddingCollectionRebuildWorker> workerProvider;
    private final TaskExecutor taskExecutor;

    public EmbeddingMigrationServiceImpl(DochubEmbeddingModelMigrationMapper mapper, UidGenerator uidGenerator,
                                         ModelRuntimeRegistry registry, EmbeddingMigrationLease lease,
                                         ObjectProvider<EmbeddingCollectionRebuildWorker> workerProvider,
                                         TaskExecutor taskExecutor) {
        this.mapper = mapper; this.uidGenerator = uidGenerator; this.registry = registry; this.lease = lease;
        this.workerProvider = workerProvider; this.taskExecutor = taskExecutor;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DochubEmbeddingModelMigration start(EmbeddingRuntimeCandidate candidate, Long operator) {
        if (mapper.countActive() > 0) throw new DochubFrameException(409, "已有向量模型重建任务正在运行");
        EmbeddingRuntimeSnapshot source = registry.captureEmbedding();
        DochubEmbeddingModelMigration job = new DochubEmbeddingModelMigration();
        job.setId(uidGenerator.getUid());
        job.setSourceConfigVersion(source.configVersion()); job.setTargetConfigVersion(candidate.configVersion());
        job.setSourceModelName(source.spec().modelName()); job.setTargetModelName(candidate.spec().modelName());
        job.setSourceDimension(source.dimension()); job.setTargetDimension(candidate.dimension());
        job.setSourceDocumentCollection(source.documentCollection()); job.setTargetDocumentCollection(candidate.documentCollection());
        job.setSourceMemoryCollection(source.memoryCollection()); job.setTargetMemoryCollection(candidate.memoryCollection());
        job.setMigrationStatus(EmbeddingMigrationStatus.PENDING.name());
        job.setDocumentTotal(0L); job.setDocumentProcessed(0L); job.setDocumentFailed(0L);
        job.setMemoryTotal(0L); job.setMemoryProcessed(0L); job.setMemoryFailed(0L);
        job.setLastDocumentChunkId(0L); job.setLastMemorySummaryId(0L); job.setLastDeltaSequence(0L);
        job.setOperator(operator); job.setLockVersion(0); job.setStartTime(new Date()); job.setCreateTime(new Date()); job.setEditTime(new Date()); job.setStatus(1);
        mapper.insert(job);
        return job;
    }

    @Override public DochubEmbeddingModelMigration current() { return mapper.findActive(); }
    @Override public DochubEmbeddingModelMigration find(Long migrationId) { return mapper.selectById(migrationId); }

    @Override public void schedule(Long migrationId) {
        taskExecutor.execute(() -> {
            if (!lease.acquire(migrationId)) return;
            EmbeddingCollectionRebuildWorker worker = workerProvider.getIfAvailable();
            if (worker != null) worker.resume(migrationId);
        });
    }

    @Override public void retry(Long migrationId) {
        DochubEmbeddingModelMigration job = mapper.selectById(migrationId);
        if (job == null || !EmbeddingMigrationStatus.FAILED.name().equals(job.getMigrationStatus())) {
            throw new DochubFrameException(409, "仅失败的向量迁移任务可以重试");
        }
        job.setMigrationStatus(resolveResumeStatus(job).name()); job.setErrorSummary(null); job.setEditTime(new Date());
        mapper.updateById(job); schedule(migrationId);
    }

    @Override public boolean finalizing() {
        DochubEmbeddingModelMigration job = current();
        return job != null && (EmbeddingMigrationStatus.FINALIZING.name().equals(job.getMigrationStatus())
            || EmbeddingMigrationStatus.VERIFYING.name().equals(job.getMigrationStatus())
            || EmbeddingMigrationStatus.SWITCHING.name().equals(job.getMigrationStatus()));
    }

    private EmbeddingMigrationStatus resolveResumeStatus(DochubEmbeddingModelMigration job) {
        if (job.getLastDocumentChunkId() == null || job.getLastDocumentChunkId() == 0) return EmbeddingMigrationStatus.PENDING;
        if (job.getLastMemorySummaryId() == null || job.getLastMemorySummaryId() == 0) return EmbeddingMigrationStatus.REBUILDING_DOCUMENTS;
        return EmbeddingMigrationStatus.CATCHING_UP;
    }
}
