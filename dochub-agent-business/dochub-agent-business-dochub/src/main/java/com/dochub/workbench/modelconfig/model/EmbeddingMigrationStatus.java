package com.dochub.workbench.modelconfig.model;

import org.javaup.exception.DochubFrameException;

import java.util.EnumSet;
import java.util.Set;

public enum EmbeddingMigrationStatus {
    PENDING, REBUILDING_DOCUMENTS, REBUILDING_MEMORY, CATCHING_UP, FINALIZING,
    VERIFYING, SWITCHING, COMPLETED, FAILED;

    public boolean canTransitionTo(EmbeddingMigrationStatus target) {
        if (target == FAILED) return this != COMPLETED && this != SWITCHING;
        return switch (this) {
            case PENDING -> target == REBUILDING_DOCUMENTS;
            case REBUILDING_DOCUMENTS -> target == REBUILDING_MEMORY;
            case REBUILDING_MEMORY -> target == CATCHING_UP;
            case CATCHING_UP -> target == FINALIZING;
            case FINALIZING -> target == VERIFYING;
            case VERIFYING -> target == SWITCHING;
            case SWITCHING -> target == COMPLETED;
            case FAILED -> Set.of(PENDING, REBUILDING_DOCUMENTS, REBUILDING_MEMORY, CATCHING_UP, FINALIZING, VERIFYING).contains(target);
            case COMPLETED -> false;
        };
    }

    public void requireTransition(EmbeddingMigrationStatus target) {
        if (!canTransitionTo(target)) throw new DochubFrameException(409, "非法向量迁移状态转换: " + this + " -> " + target);
    }

    public boolean active() {
        return EnumSet.of(PENDING, REBUILDING_DOCUMENTS, REBUILDING_MEMORY, CATCHING_UP, FINALIZING, VERIFYING, SWITCHING).contains(this);
    }
}
