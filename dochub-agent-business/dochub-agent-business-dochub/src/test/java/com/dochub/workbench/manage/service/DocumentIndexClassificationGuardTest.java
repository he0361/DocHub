package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.model.classify.ClassificationStatus;
import com.dochub.workbench.manage.support.DocumentClassificationIndexGuard;
import org.javaup.enums.DocumentManageCode;
import org.javaup.exception.DochubFrameException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentIndexClassificationGuardTest {

    @Test
    void pendingReviewCannotQueueIndexBuild() {
        DochubDocument document = new DochubDocument();
        document.setClassificationStatus(ClassificationStatus.PENDING_REVIEW.name());

        assertThatThrownBy(() -> DocumentClassificationIndexGuard.requireConfirmed(document))
            .isInstanceOfSatisfying(DochubFrameException.class, exception ->
                assertThat(exception.getCode()).isEqualTo(DocumentManageCode.KNOWLEDGE_CLASSIFICATION_PENDING.getCode()))
            .hasMessageContaining("知识域待确认");
    }

    @Test
    void consumerFailureCodeUsesTheSameStableStatusMapping() {
        assertThat(DocumentClassificationIndexGuard.failureCode(ClassificationStatus.PENDING_REVIEW.name()))
            .isEqualTo("KNOWLEDGE_CLASSIFICATION_PENDING");
        assertThat(DocumentClassificationIndexGuard.failureCode(ClassificationStatus.FAILED.name()))
            .isEqualTo("KNOWLEDGE_CLASSIFICATION_FAILED");
    }
}
