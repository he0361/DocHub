package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class EmbeddingMigrationLease {
    private final DochubEmbeddingModelMigrationMapper mapper;
    private final String owner = UUID.randomUUID().toString();
    private final Set<Long> localClaims = ConcurrentHashMap.newKeySet();

    public EmbeddingMigrationLease(DochubEmbeddingModelMigrationMapper mapper) { this.mapper = mapper; }

    public boolean acquire(Long migrationId) {
        if (migrationId == null || !localClaims.add(migrationId)) return false;
        Date expiry = new Date(System.currentTimeMillis() + Duration.ofSeconds(30).toMillis());
        boolean acquired = mapper.acquireLease(migrationId, owner, expiry) == 1;
        if (!acquired) localClaims.remove(migrationId);
        return acquired;
    }

    public boolean heartbeat(Long migrationId) {
        if (migrationId == null || !localClaims.contains(migrationId)) return false;
        Date expiry = new Date(System.currentTimeMillis() + Duration.ofSeconds(30).toMillis());
        return mapper.acquireLease(migrationId, owner, expiry) == 1;
    }

    public void release(Long migrationId) {
        if (migrationId == null) return;
        try { mapper.releaseLease(migrationId, owner); }
        finally { localClaims.remove(migrationId); }
    }
}
