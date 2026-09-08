package com.dochub.workbench.modelconfig.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

@Data
@TableName("dochub_embedding_migration_delta")
@EqualsAndHashCode(callSuper = true)
public class DochubEmbeddingMigrationDelta extends BaseTableData {
    @TableId(type = IdType.INPUT) private Long id;
    private Long migrationId;
    private String resourceType;
    private Long resourceId;
    private String operation;
    private Long sequenceNo;
    private String deltaStatus;
    private Integer attempts;
    private String errorSummary;
}
