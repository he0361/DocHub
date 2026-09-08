package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import com.dochub.workbench.manage.data.DochubDocumentChunk;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/

@Mapper
public interface DochubDocumentChunkMapper extends BaseMapper<DochubDocumentChunk> {
    @Select("SELECT * FROM dochub_document_chunk WHERE id > #{afterId} AND status=1 AND vector_status=3 ORDER BY id LIMIT #{limit}")
    List<DochubDocumentChunk> selectMigrationBatch(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM dochub_document_chunk WHERE status=1 AND vector_status=3")
    long countMigrationSource();
}
