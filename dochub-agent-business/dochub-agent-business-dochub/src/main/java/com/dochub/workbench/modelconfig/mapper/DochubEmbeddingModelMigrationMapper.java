package com.dochub.workbench.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;

@Mapper
public interface DochubEmbeddingModelMigrationMapper extends BaseMapper<DochubEmbeddingModelMigration> {
    @Select("SELECT COUNT(*) FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING') AND status=1")
    long countActive();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING') AND status=1 ORDER BY id")
    List<DochubEmbeddingModelMigration> findRecoverable();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING') AND status=1 ORDER BY id DESC LIMIT 1")
    DochubEmbeddingModelMigration findActive();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE status=1 ORDER BY id DESC LIMIT 1")
    DochubEmbeddingModelMigration findLatest();

    @Update("UPDATE dochub_embedding_model_migration SET lease_owner=#{owner}, lease_expire_time=#{expiry}, edit_time=NOW() WHERE id=#{id} AND (lease_expire_time IS NULL OR lease_expire_time < NOW() OR lease_owner=#{owner})")
    int acquireLease(@Param("id") Long id, @Param("owner") String owner, @Param("expiry") Date expiry);
}
