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
    @Select("SELECT lock_name FROM dochub_embedding_migration_lock WHERE lock_name='embedding-model' FOR UPDATE")
    String lockMigrationSlot();

    @Select("SELECT COUNT(*) FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING') AND status=1")
    long countActive();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING') AND status=1 ORDER BY id")
    List<DochubEmbeddingModelMigration> findRecoverable();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING') AND status=1 ORDER BY id DESC LIMIT 1")
    DochubEmbeddingModelMigration findActive();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FINALIZING','VERIFYING','SWITCHING','FAILED') AND status=1 ORDER BY id DESC LIMIT 1")
    DochubEmbeddingModelMigration findMutationTarget();

    @Select("SELECT * FROM dochub_embedding_model_migration WHERE status=1 ORDER BY id DESC LIMIT 1")
    DochubEmbeddingModelMigration findLatest();

    @Update("UPDATE dochub_embedding_model_migration SET lease_owner=#{owner}, lease_expire_time=#{expiry}, edit_time=NOW() WHERE id=#{id} AND (lease_expire_time IS NULL OR lease_expire_time < NOW() OR lease_owner=#{owner})")
    int acquireLease(@Param("id") Long id, @Param("owner") String owner, @Param("expiry") Date expiry);

    @Update("UPDATE dochub_embedding_model_migration SET lease_owner=NULL, lease_expire_time=NULL, edit_time=NOW() WHERE id=#{id} AND lease_owner=#{owner}")
    int releaseLease(@Param("id") Long id, @Param("owner") String owner);

    @Update("UPDATE dochub_embedding_model_migration SET active_mutations=active_mutations+1, edit_time=NOW() WHERE id=#{id} AND migration_status IN ('PENDING','REBUILDING_DOCUMENTS','REBUILDING_MEMORY','CATCHING_UP','FAILED') AND status=1")
    int beginMutation(@Param("id") Long id);

    @Update("UPDATE dochub_embedding_model_migration SET active_mutations=GREATEST(active_mutations-1,0), edit_time=NOW() WHERE id=#{id} AND status=1")
    int finishMutation(@Param("id") Long id);

    @Update("UPDATE dochub_embedding_model_migration SET migration_status='FINALIZING', edit_time=NOW() WHERE id=#{id} AND migration_status='CATCHING_UP' AND active_mutations=0 AND status=1")
    int enterFinalizing(@Param("id") Long id);
}
