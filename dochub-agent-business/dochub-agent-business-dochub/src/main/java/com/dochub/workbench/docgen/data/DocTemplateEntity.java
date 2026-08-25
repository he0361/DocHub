package com.dochub.workbench.docgen.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

/**
 * 文枢 DocHub 文档模板实体。
 *
 * <p>模板是"Markdown 骨架 + {{变量}} 占位符"的唯一事实源：LLM 生成正文时以此为约束，
 * 前端以 variable_schema 动态渲染填写表单。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_doc_template")
@EqualsAndHashCode(callSuper = true)
public class DocTemplateEntity extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String templateCode;

    private String templateName;

    private String templateType;

    private String knowledgeScopeCode;

    private String description;

    private String outlinePrompt;

    private String contentTemplateText;

    private String variableSchema;

    private String outputFormats;

    private Integer version;
}
