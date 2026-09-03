package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/
@Mapper
public interface DochubKnowledgeScopeNodeMapper extends BaseMapper<DochubKnowledgeScopeNode> {
    @Select("SELECT * FROM dochub_knowledge_scope_node WHERE scope_code=#{scopeCode} AND status=1 LIMIT 1")
    DochubKnowledgeScopeNode selectActiveByCode(@Param("scopeCode") String scopeCode);

    @Update("UPDATE dochub_knowledge_scope_node SET parent_scope_code=#{targetCode}, edit_time=NOW() " +
        "WHERE parent_scope_code=#{sourceCode} AND status=1")
    int replaceParentScopeCode(@Param("sourceCode") String sourceCode, @Param("targetCode") String targetCode);

    @Update("UPDATE dochub_knowledge_scope_node SET status=0, " +
        "canonical_key=LEFT(CONCAT(canonical_key,'#merged#',scope_code),191), edit_time=NOW() " +
        "WHERE scope_code=#{scopeCode} AND status=1")
    int deactivateByScopeCode(@Param("scopeCode") String scopeCode);

    @Select("SELECT GET_LOCK(CONCAT('dochub:scope:', #{canonicalKey}), 5)")
    Integer acquireCanonicalLock(@Param("canonicalKey") String canonicalKey);

    @Select("SELECT RELEASE_LOCK(CONCAT('dochub:scope:', #{canonicalKey}))")
    Integer releaseCanonicalLock(@Param("canonicalKey") String canonicalKey);
}
