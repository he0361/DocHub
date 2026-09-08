package com.dochub.workbench.modelconfig.vo;

import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;

import java.util.Date;

public record EmbeddingMigrationVo(Long migrationId, String status, Long sourceConfigVersion,
                                   Long targetConfigVersion, String sourceModelName, String targetModelName,
                                   long documentTotal, long documentProcessed, long documentFailed,
                                   long memoryTotal, long memoryProcessed, long memoryFailed,
                                   String errorSummary, Date startTime, Date finishTime) {
    public static EmbeddingMigrationVo from(DochubEmbeddingModelMigration job) {
        if (job == null) return null;
        return new EmbeddingMigrationVo(job.getId(), job.getMigrationStatus(), job.getSourceConfigVersion(),
            job.getTargetConfigVersion(), job.getSourceModelName(), job.getTargetModelName(),
            value(job.getDocumentTotal()), value(job.getDocumentProcessed()), value(job.getDocumentFailed()),
            value(job.getMemoryTotal()), value(job.getMemoryProcessed()), value(job.getMemoryFailed()),
            job.getErrorSummary(), job.getStartTime(), job.getFinishTime());
    }

    private static long value(Long number) { return number == null ? 0L : number; }
}
