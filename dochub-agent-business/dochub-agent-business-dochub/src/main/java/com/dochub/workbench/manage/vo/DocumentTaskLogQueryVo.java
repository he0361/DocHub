package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTaskLogQueryVo {

    private Long taskId;

    private Long documentId;

    private Integer taskType;

    private String taskTypeName;

    private Integer taskStatus;

    private String taskStatusName;

    private Integer currentStage;

    private String currentStageName;

    private Date startTime;

    private Date finishTime;

    private Long costMillis;

    private String errorCode;

    private String errorMsg;

    private Long total;

    private List<DocumentTaskLogVo> logs;
}
