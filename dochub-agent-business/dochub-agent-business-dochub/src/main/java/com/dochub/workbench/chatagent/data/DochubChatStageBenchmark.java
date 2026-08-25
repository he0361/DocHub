package com.dochub.workbench.chatagent.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("dochub_chat_stage_benchmark")
@EqualsAndHashCode(callSuper = true)
public class DochubChatStageBenchmark extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    @TableField("stage_code")
    private String stageCode;

    @TableField("execution_mode")
    private String executionMode;

    @TableField("p50_duration_ms")
    private Long p50DurationMs;

    @TableField("p90_duration_ms")
    private Long p90DurationMs;

    @TableField("p99_duration_ms")
    private Long p99DurationMs;

    @TableField("avg_duration_ms")
    private Long avgDurationMs;

    @TableField("max_duration_ms")
    private Long maxDurationMs;

    @TableField("min_duration_ms")
    private Long minDurationMs;

    @TableField("sample_count")
    private Integer sampleCount;

    @TableField("recent_durations")
    private String recentDurations;

    @TableField("last_update_time")
    private Date lastUpdateTime;
}
