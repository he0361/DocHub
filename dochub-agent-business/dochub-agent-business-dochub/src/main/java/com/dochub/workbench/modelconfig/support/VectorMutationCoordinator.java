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

/** Journals source-of-truth mutations after the active runtime mutation succeeds. */
@Component
public class VectorMutationCoordinator {
    private final EmbeddingMigrationService migrations;
    private final DochubEmbeddingMigrationDeltaMapper mapper;
    private final UidGenerator uidGenerator;

    public VectorMutationCoordinator(EmbeddingMigrationService migrations,
                                     DochubEmbeddingMigrationDeltaMapper mapper, UidGenerator uidGenerator) {
        this.migrations = migrations; this.mapper = mapper; this.uidGenerator = uidGenerator;
    }

    public void assertMutationAllowed() {
        if (migrations.finalizing()) throw new DochubFrameException(409, "向量模型正在完成安全切换，请稍后重试");
    }

    public MutationPermit beginMutation() {
        return new MutationPermit(migrations, migrations.beginMutation());
    }

    public void documentUpsert(MutationPermit permit, List<DochubDocumentChunk> chunks) {
        if (chunks == null) return;
        for (DochubDocumentChunk chunk : chunks) if (chunk != null && chunk.getId() != null) append(permit, "DOCUMENT_CHUNK", chunk.getId(), "UPSERT");
    }
    public void documentDelete(MutationPermit permit, Long documentId) { if (documentId != null) append(permit, "DOCUMENT", documentId, "DELETE_DOCUMENT"); }
    public void memoryUpsert(MutationPermit permit, Long summaryId) { if (summaryId != null) append(permit, "MEMORY", summaryId, "UPSERT"); }
    public void memoryDelete(MutationPermit permit, Long summaryId) { if (summaryId != null) append(permit, "MEMORY", summaryId, "DELETE"); }

    private void append(MutationPermit permit, String type, Long resourceId, String operation) {
        if (permit == null || permit.migrationId() == null) return;
        DochubEmbeddingMigrationDelta delta = new DochubEmbeddingMigrationDelta();
        long id = uidGenerator.getUid();
        delta.setId(id); delta.setMigrationId(permit.migrationId()); delta.setResourceType(type);
        delta.setResourceId(resourceId); delta.setOperation(operation); delta.setSequenceNo(id);
        delta.setDeltaStatus("PENDING"); delta.setAttempts(0); delta.setCreateTime(new Date()); delta.setEditTime(new Date()); delta.setStatus(1);
        mapper.insert(delta);
    }

    public static final class MutationPermit implements AutoCloseable {
        private final EmbeddingMigrationService migrations;
        private final Long migrationId;
        private boolean closed;

        private MutationPermit(EmbeddingMigrationService migrations, Long migrationId) {
            this.migrations = migrations;
            this.migrationId = migrationId;
        }

        public Long migrationId() { return migrationId; }

        @Override public void close() {
            if (!closed) {
                closed = true;
                migrations.finishMutation(migrationId);
            }
        }
    }
}
