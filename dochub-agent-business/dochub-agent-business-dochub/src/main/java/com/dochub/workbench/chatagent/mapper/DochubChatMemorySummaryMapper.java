package com.dochub.workbench.chatagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.dochub.workbench.chatagent.data.DochubChatMemorySummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/

@Mapper
public interface DochubChatMemorySummaryMapper extends BaseMapper<DochubChatMemorySummary> {
    @Select("SELECT * FROM dochub_chat_memory_summary WHERE id > #{afterId} AND status=1 AND summary_text IS NOT NULL AND summary_text<>'' ORDER BY id LIMIT #{limit}")
    List<DochubChatMemorySummary> selectMigrationBatch(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM dochub_chat_memory_summary WHERE status=1 AND summary_text IS NOT NULL AND summary_text<>''")
    long countMigrationSource();
}
