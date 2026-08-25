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
 * 文枢 DocHub 文档生成历史记录实体。
 *
 * <p>每份生成的文档都会落一条记录：支持重新下载、失败重试、审计（token/耗时），
 * 并通过 sourceDocumentId 与"一键入库"后的知识库文档形成闭环。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_doc_generation_record")
@EqualsAndHashCode(callSuper = true)
public class DocGenerationRecordEntity extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String recordCode;

    private Long templateId;

    private String templateName;

    private String generationMode;

    private String userRequirement;

    private String variablesJson;

    private String generatedMarkdown;

    private String outputFormat;

    private String fileName;

    private String storageObjectName;

    private Long sourceDocumentId;

    /** 参考文档仿写模式下，参考文档的 id（上传参考文件时为空） */
    private Long referenceDocumentId;

    private Integer generationStatus;

    private String errorMsg;

    private String modelProvider;

    private String modelName;

    private Integer promptTokens;

    private Integer completionTokens;

    private Long costMillis;
}
