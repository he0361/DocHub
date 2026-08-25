package com.dochub.workbench.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

import java.math.BigDecimal;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据实体
 * @author: zhangjihe
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_topic_document_relation")
@EqualsAndHashCode(callSuper = true)
public class DochubTopicDocumentRelation extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String topicCode;

    private Long documentId;

    private BigDecimal relationScore;

    private String relationSource;

    private String reason;
}
