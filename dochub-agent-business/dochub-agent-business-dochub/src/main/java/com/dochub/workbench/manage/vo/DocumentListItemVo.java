package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListItemVo {

    private Long documentId;

    private String documentName;

    private String originalFileName;

    private Integer fileType;

    private String fileTypeName;

    private Long fileSize;

    private Integer charCount;

    private Integer tokenCount;

    private Integer parseStatus;

    private String parseStatusName;

    private Integer strategyStatus;

    private String strategyStatusName;

    private Integer indexStatus;

    private String indexStatusName;

    private String parseErrorMsg;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String businessCategory;

    private String documentTags;

    private Long currentPlanId;

    private Long lastIndexTaskId;

    private Long latestTaskId;

    private Integer latestTaskType;

    private String latestTaskTypeName;

    private Integer latestTaskStatus;

    private String latestTaskStatusName;

    /** 最近任务当前阶段（供前端展示解析/索引进度条） */
    private Integer currentStage;

    private String currentStageName;

    private Date createTime;

    private Date editTime;
}
