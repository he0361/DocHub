package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.model.EmbeddingMigrationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
