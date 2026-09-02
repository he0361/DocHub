package com.dochub.workbench.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingMigrationDelta;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DochubEmbeddingMigrationDeltaMapper extends BaseMapper<DochubEmbeddingMigrationDelta> {
    @Select("SELECT * FROM dochub_embedding_migration_delta WHERE migration_id=#{migrationId} AND sequence_no>#{after} AND delta_status='PENDING' AND status=1 ORDER BY sequence_no LIMIT #{limit}")
    List<DochubEmbeddingMigrationDelta> nextPending(@Param("migrationId") Long migrationId, @Param("after") long after, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM dochub_embedding_migration_delta WHERE migration_id=#{migrationId} AND delta_status='PENDING' AND status=1")
    long countPending(@Param("migrationId") Long migrationId);
}
