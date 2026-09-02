package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class EmbeddingMigrationLease {
    private final DochubEmbeddingModelMigrationMapper mapper;
    private final String owner = UUID.randomUUID().toString();

    public EmbeddingMigrationLease(DochubEmbeddingModelMigrationMapper mapper) { this.mapper = mapper; }

    public boolean acquire(Long migrationId) {
        Date expiry = new Date(System.currentTimeMillis() + Duration.ofSeconds(30).toMillis());
        return mapper.acquireLease(migrationId, owner, expiry) == 1;
    }
}
