package com.dochub.workbench.skill.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

/**
 * 文枢 DocHub 技能注册表实体。
 *
 * <p>技能是"数据资产"而非 Spring Bean：元数据入库（可搜索/启停/统计），
 * 原始内容（SKILL.md + scripts/references）保存在 MinIO 或 classpath。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_doc_skill")
@EqualsAndHashCode(callSuper = true)
public class DocSkillEntity extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String skillName;

    private String displayName;

    private String version;

    private String description;

    private String whenToUse;

    private String instructions;

    private String skillType;

    private String category;

    private String tags;

    private String author;

    private String sourceType;

    private String objectPrefix;

    private String contentSnapshot;

    private Integer scriptExecEnabled;

    private Integer runState;

    private Integer installCount;

    private java.util.Date lastUsedTime;
}
