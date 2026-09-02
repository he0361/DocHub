package com.dochub.workbench.manage.support;

import com.dochub.workbench.manage.data.DochubDocument;
import com.dochub.workbench.manage.model.classify.ClassificationStatus;
import org.javaup.enums.DocumentManageCode;
import org.javaup.exception.DochubFrameException;

/** Central status mapping shared by the index producer and consumer. */
public final class DocumentClassificationIndexGuard {

    private DocumentClassificationIndexGuard() {
    }

    public static boolean isConfirmed(String status) {
        return ClassificationStatus.CONFIRMED.name().equals(status);
    }

    public static String failureCode(String status) {
        return ClassificationStatus.PENDING_REVIEW.name().equals(status)
            ? "KNOWLEDGE_CLASSIFICATION_PENDING"
            : "KNOWLEDGE_CLASSIFICATION_FAILED";
    }

    public static void requireConfirmed(DochubDocument document) {
        String status = document == null ? null : document.getClassificationStatus();
        if (ClassificationStatus.PENDING_REVIEW.name().equals(status)) {
            throw new DochubFrameException(DocumentManageCode.KNOWLEDGE_CLASSIFICATION_PENDING.getCode(),
                "KNOWLEDGE_CLASSIFICATION_PENDING：知识域待确认，请先选择现有知识域/主题或相信 LLM 提案。");
        }
        if (!isConfirmed(status)) {
            throw new DochubFrameException(DocumentManageCode.KNOWLEDGE_CLASSIFICATION_FAILED.getCode(),
                "KNOWLEDGE_CLASSIFICATION_FAILED：知识域分类未完成，不能构建索引。");
        }
    }
}
