package com.dochub.workbench.manage.vo;

import lombok.Data;

/**
 * 文枢 DocHub 索引构建实时进度。
 *
 * <p>由 {@code DocumentIndexBuildProgressService} 在索引构建过程中实时维护（内存态），
 * 前端轮询该对象展示"切块执行→切块后处理→向量化→关键词索引→入库完成"各阶段的分段进度与详情。</p>
 */
@Data
public class DocumentIndexBuildProgressVo {

    private Long documentId;

    private Long taskId;

    /** 当前所处阶段编码（对应 DocumentTaskStageEnum） */
    private Integer stageCode;

    /** 当前所处阶段名称 */
    private String stageName;

    /** 整体进度 0~100 */
    private Integer percent;

    /** 人类可读的进度详情，例如"向量化批次 3/10" */
    private String message;

    /** 是否已执行完成 */
    private Boolean finished;

    /** 是否执行失败 */
    private Boolean failed;

    /** 最后更新时间戳（毫秒） */
    private Long updatedAt;
}
