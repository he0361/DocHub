package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.service.EmbeddingMigrationService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingMigrationRecoveryTask {
    private final DochubEmbeddingModelMigrationMapper mapper;
    private final EmbeddingMigrationService service;
    public EmbeddingMigrationRecoveryTask(DochubEmbeddingModelMigrationMapper mapper, EmbeddingMigrationService service) {
        this.mapper = mapper; this.service = service;
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() { mapper.findRecoverable().forEach(job -> service.schedule(job.getId())); }
}
