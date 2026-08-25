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
@TableName("dochub_knowledge_route_trace")
@EqualsAndHashCode(callSuper = true)
public class DochubKnowledgeRouteTrace extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String conversationId;

    private Long exchangeId;

    private String question;

    private String rewriteQuestion;

    private String mode;

    private String topScopesJson;

    private String topTopicsJson;

    private String topDocumentsJson;

    private Long selectedDocumentId;

    private Integer hitSelectedDocument;

    private BigDecimal confidence;

    private Integer routeStatus;

    private String errorMsg;
}
