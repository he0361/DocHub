package com.dochub.workbench.modelconfig.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

import java.util.Date;

@Data
@TableName("dochub_embedding_model_migration")
@EqualsAndHashCode(callSuper = true)
public class DochubEmbeddingModelMigration extends BaseTableData {
    @TableId(type = IdType.INPUT) private Long id;
    private Long sourceConfigVersion;
    private Long targetConfigVersion;
    private String sourceModelName;
    private String targetModelName;
    private Integer sourceDimension;
    private Integer targetDimension;
    private String sourceDocumentCollection;
    private String targetDocumentCollection;
    private String sourceMemoryCollection;
    private String targetMemoryCollection;
    private String migrationStatus;
    private Long documentTotal;
    private Long documentProcessed;
    private Long documentFailed;
    private Long memoryTotal;
    private Long memoryProcessed;
    private Long memoryFailed;
    private Long lastDocumentChunkId;
    private Long lastMemorySummaryId;
    private Long lastDeltaSequence;
    private String leaseOwner;
    private Date leaseExpireTime;
    private String errorSummary;
    private Date startTime;
    private Date switchTime;
    private Date finishTime;
    private Long operator;
    private Integer lockVersion;
}
