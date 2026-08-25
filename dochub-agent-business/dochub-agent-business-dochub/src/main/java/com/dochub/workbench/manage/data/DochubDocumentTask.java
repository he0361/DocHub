package com.dochub.workbench.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

import java.util.Date;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据实体
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_document_task")
@EqualsAndHashCode(callSuper = true)
public class DochubDocumentTask extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Long planId;

    private Integer taskType;

    private Integer taskStatus;

    private Integer currentStage;

    private Integer triggerSource;

    private String strategySnapshot;

    private Integer retryCount;

    private Date startTime;

    private Date finishTime;

    private Long costMillis;

    private String errorCode;

    private String errorMsg;

    private String extJson;
}
