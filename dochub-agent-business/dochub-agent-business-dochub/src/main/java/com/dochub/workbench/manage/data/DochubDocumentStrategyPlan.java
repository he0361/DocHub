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
@TableName("dochub_document_strategy_plan")
@EqualsAndHashCode(callSuper = true)
public class DochubDocumentStrategyPlan extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Integer planVersion;

    private Integer planSource;

    private Integer planStatus;

    private Integer strategyCount;

    private String strategySnapshot;

    private String recommendReason;

    private String adjustNote;

    private Long confirmUserId;

    private Date confirmTime;
}
