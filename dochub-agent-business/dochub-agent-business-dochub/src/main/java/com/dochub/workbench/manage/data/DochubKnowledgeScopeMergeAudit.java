package com.dochub.workbench.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dochub_knowledge_scope_merge_audit")
public class DochubKnowledgeScopeMergeAudit extends BaseTableData {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String sourceScopeCode;
    private String targetScopeCode;
    private Integer documentCount;
    private Integer topicCount;
    private Integer relationCount;
    private String operator;
    private String detailJson;
}
