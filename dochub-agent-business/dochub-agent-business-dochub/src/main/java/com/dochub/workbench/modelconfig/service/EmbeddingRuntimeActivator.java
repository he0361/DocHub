package com.dochub.workbench.modelconfig.service;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.EmbeddingMigrationStatus;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.Date;

/** Commits database state first, then publishes one complete immutable embedding snapshot. */
@Component
public class EmbeddingRuntimeActivator {
    private final DochubAiModelConfigMapper configMapper;
    private final DochubEmbeddingModelMigrationMapper migrationMapper;
    private final DochubAiModelConfigAuditMapper auditMapper;
    private final UidGenerator uidGenerator;
    private final ModelRuntimeRegistry registry;
    private final ModelConfigVersionPublisher publisher;

    public EmbeddingRuntimeActivator(DochubAiModelConfigMapper configMapper,
                                     DochubEmbeddingModelMigrationMapper migrationMapper,
                                     DochubAiModelConfigAuditMapper auditMapper, UidGenerator uidGenerator,
                                     ModelRuntimeRegistry registry, ModelConfigVersionPublisher publisher) {
        this.configMapper = configMapper;
        this.migrationMapper = migrationMapper;
        this.auditMapper = auditMapper;
        this.uidGenerator = uidGenerator;
        this.registry = registry;
        this.publisher = publisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void activate(DochubEmbeddingModelMigration job, DochubAiModelConfig config,
                         EmbeddingRuntimeSnapshot target, Long operator, String action) {
        Date now = new Date();
        configMapper.update(null, new UpdateWrapper<DochubAiModelConfig>()
            .eq("model_type", ModelType.EMBEDDING.name())
            .eq("active", 1)
            .set("active", 0)
            .set("edit_time", now));
        config.setActive(1);
        config.setEditTime(now);
        configMapper.updateById(config);
        if (job != null) {
            job.setMigrationStatus(EmbeddingMigrationStatus.COMPLETED.name());
            job.setSwitchTime(now);
            job.setFinishTime(now);
            job.setErrorSummary(null);
            job.setEditTime(now);
            migrationMapper.updateById(job);
        }
        auditMapper.insert(new DochubAiModelConfigAudit(uidGenerator.getUid(), config.getId(),
            ModelType.EMBEDDING.name(), config.getConfigVersion(), action, 1,
            maskedEndpoint(config.getBaseUrl()), operator, null, now));
        Runnable publish = () -> {
            registry.activateEmbedding(target);
            try { publisher.publish(target.configVersion()); }
            catch (RuntimeException ignored) { /* database polling is the recovery path */ }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else {
            publish.run();
        }
    }

    private String maskedEndpoint(String endpoint) {
        try {
            URI uri = URI.create(endpoint);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        } catch (RuntimeException ignored) { return "<invalid-endpoint>"; }
    }
}
