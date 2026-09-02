package com.dochub.workbench.modelconfig.support;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingMigrationDelta;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingMigrationDeltaMapper;
import com.dochub.workbench.modelconfig.service.EmbeddingMigrationService;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Journals source-of-truth mutations after the active runtime mutation succeeds. */
@Component
public class VectorMutationCoordinator {
    private final EmbeddingMigrationService migrations;
    private final DochubEmbeddingMigrationDeltaMapper mapper;
    private final UidGenerator uidGenerator;
    private final AtomicLong sequences = new AtomicLong(System.currentTimeMillis() * 1000);

    public VectorMutationCoordinator(EmbeddingMigrationService migrations,
                                     DochubEmbeddingMigrationDeltaMapper mapper, UidGenerator uidGenerator) {
        this.migrations = migrations; this.mapper = mapper; this.uidGenerator = uidGenerator;
    }

    public void assertMutationAllowed() {
        if (migrations.finalizing()) throw new DochubFrameException(409, "向量模型正在完成安全切换，请稍后重试");
    }

    public void documentUpsert(List<DochubDocumentChunk> chunks) {
        if (chunks == null) return;
        for (DochubDocumentChunk chunk : chunks) if (chunk != null && chunk.getId() != null) append("DOCUMENT", chunk.getId(), "UPSERT");
    }
    public void documentDelete(Long documentId) { if (documentId != null) append("DOCUMENT", documentId, "DELETE_DOCUMENT"); }
    public void memoryUpsert(Long summaryId) { if (summaryId != null) append("MEMORY", summaryId, "UPSERT"); }

    private void append(String type, Long resourceId, String operation) {
        DochubEmbeddingModelMigration job = migrations.current();
        if (job == null) return;
        DochubEmbeddingMigrationDelta delta = new DochubEmbeddingMigrationDelta();
        delta.setId(uidGenerator.getUid()); delta.setMigrationId(job.getId()); delta.setResourceType(type);
        delta.setResourceId(resourceId); delta.setOperation(operation); delta.setSequenceNo(sequences.incrementAndGet());
        delta.setDeltaStatus("PENDING"); delta.setAttempts(0); delta.setCreateTime(new Date()); delta.setEditTime(new Date()); delta.setStatus(1);
        mapper.insert(delta);
    }
}
