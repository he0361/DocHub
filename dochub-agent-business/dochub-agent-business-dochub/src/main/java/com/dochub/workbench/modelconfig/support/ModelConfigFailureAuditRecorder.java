package com.dochub.workbench.modelconfig.support;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/** Persists failure evidence independently so a caller rollback cannot erase it. */
@Component
public class ModelConfigFailureAuditRecorder {
    private final DochubAiModelConfigAuditMapper mapper; private final UidGenerator ids;
    public ModelConfigFailureAuditRecorder(DochubAiModelConfigAuditMapper mapper, UidGenerator ids) { this.mapper = mapper; this.ids = ids; }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long operator, String endpoint, String error) {
        try { mapper.insert(new DochubAiModelConfigAudit(ids.getUid(), null, "CHAT", null, "TEST", 0, endpoint, operator, error, new Date())); }
        catch (RuntimeException ignored) { }
    }
}
