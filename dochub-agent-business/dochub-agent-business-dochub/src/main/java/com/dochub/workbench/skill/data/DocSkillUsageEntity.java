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
 * 文枢 DocHub 技能调用记录实体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("dochub_doc_skill_usage")
@EqualsAndHashCode(callSuper = true)
public class DocSkillUsageEntity extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long skillId;

    private String conversationId;

    private Long exchangeId;

    private String scene;

    private java.math.BigDecimal matchedScore;

    private String matchedReason;
}
