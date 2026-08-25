package com.dochub.workbench.manage.support;

import com.dochub.workbench.manage.vo.DocumentIndexBuildProgressVo;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.DocumentTaskStageEnum;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文枢 DocHub 索引构建实时进度注册表（内存态）。
 *
 * <p>索引构建的每个子步骤（切块、后处理、向量化批次、关键词索引、入库完成）都会实时上报进度，
 * 前端轮询 {@link #get} 展示分段进度条与阶段内详情。进度只服务"当前正在构建"的展示，不需要持久化；
 * 构建结束（成功/失败）后，条目会在清理任务中自动移除。</p>
 */
@Slf4j
@Component
public class DocumentIndexBuildProgressService {

    /** 向量化阶段整体进度区间 */
    private static final int VECTORIZE_PERCENT_FLOOR = 70;
    private static final int VECTORIZE_PERCENT_CEIL = 88;

    /** 已结束条目保留时长：给前端留出展示"完成/失败"的时间窗口后清除 */
    private static final long FINISHED_KEEP_MILLIS = 5 * 60 * 1000L;

    /** 任何条目超过该时长未更新即视为过期，防止内存泄漏 */
    private static final long STALE_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Map<Long, DocumentIndexBuildProgressVo> registry = new ConcurrentHashMap<>();

    /**
     * 索引构建开始：进入"切块执行"阶段。
     */
    public void start(Long documentId, Long taskId) {
        reportStage(documentId, taskId, DocumentTaskStageEnum.CHUNK_EXECUTE, 60, "开始切块执行");
    }

    /**
     * 上报某个阶段的整体进度。
     */
    public void reportStage(Long documentId, Long taskId,
                            DocumentTaskStageEnum stage, int percent, String message) {
        DocumentIndexBuildProgressVo vo = build(documentId, taskId, stage.getCode(), percent, message, false, false);
        registry.put(documentId, vo);
    }

    /**
     * 向量化阶段内，按批次上报进度，例如"向量化批次 3/10"。
     */
    public void reportVectorizeBatch(Long documentId, Long taskId, int batchIndex, int totalBatches) {
        int range = VECTORIZE_PERCENT_CEIL - VECTORIZE_PERCENT_FLOOR;
        int percent = VECTORIZE_PERCENT_FLOOR
            + Math.round(range * (float) batchIndex / Math.max(1, totalBatches));
        percent = Math.min(VECTORIZE_PERCENT_CEIL, Math.max(VECTORIZE_PERCENT_FLOOR, percent));
        String message = "向量化批次 " + batchIndex + "/" + totalBatches;
        DocumentIndexBuildProgressVo vo = build(documentId, taskId,
            DocumentTaskStageEnum.VECTORIZE.getCode(), percent, message, false, false);
        registry.put(documentId, vo);
    }

    /**
     * 索引构建完成。
     */
    public void finish(Long documentId, Long taskId) {
        DocumentIndexBuildProgressVo vo = build(documentId, taskId,
            DocumentTaskStageEnum.STORE_COMPLETE.getCode(), 100, "索引构建完成", true, false);
        registry.put(documentId, vo);
    }

    /**
     * 索引构建失败。
     */
    public void fail(Long documentId, Long taskId, String message) {
        DocumentIndexBuildProgressVo vo = build(documentId, taskId,
            DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(), 100, message, false, true);
        registry.put(documentId, vo);
    }

    /**
     * 主动清除某文档的进度（例如文档被删除）。
     */
    public void clear(Long documentId) {
        registry.remove(documentId);
    }

    /**
     * 查询某文档当前的构建进度；不存在时返回 null。
     */
    public DocumentIndexBuildProgressVo get(Long documentId) {
        return registry.get(documentId);
    }

    private DocumentIndexBuildProgressVo build(Long documentId, Long taskId,
                                               Integer stageCode, int percent,
                                               String message, boolean finished, boolean failed) {
        DocumentIndexBuildProgressVo vo = new DocumentIndexBuildProgressVo();
        vo.setDocumentId(documentId);
        vo.setTaskId(taskId);
        vo.setStageCode(stageCode);
        DocumentTaskStageEnum stageEnum = DocumentTaskStageEnum.getRc(stageCode);
        vo.setStageName(stageEnum == null ? "" : stageEnum.getMsg());
        vo.setPercent(percent);
        vo.setMessage(message);
        vo.setFinished(finished);
        vo.setFailed(failed);
        vo.setUpdatedAt(System.currentTimeMillis());
        return vo;
    }

    /**
     * 定期清理：移除已结束且超过保留窗口的条目，以及长时间未更新的过期条目。
     */
    @Scheduled(fixedDelay = 5 * 60 * 1000L, initialDelay = 2 * 60 * 1000L)
    public void cleanupExpired() {
        long now = System.currentTimeMillis();
        registry.entrySet().removeIf(entry -> {
            DocumentIndexBuildProgressVo vo = entry.getValue();
            if (vo == null) {
                return true;
            }
            long updatedAt = vo.getUpdatedAt() == null ? 0L : vo.getUpdatedAt();
            boolean finishedExpired = Boolean.TRUE.equals(vo.getFinished()) && now - updatedAt > FINISHED_KEEP_MILLIS;
            boolean failedExpired = Boolean.TRUE.equals(vo.getFailed()) && now - updatedAt > FINISHED_KEEP_MILLIS;
            boolean stale = now - updatedAt > STALE_TIMEOUT_MILLIS;
            return finishedExpired || failedExpired || stale;
        });
    }
}
