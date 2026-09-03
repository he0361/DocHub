package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/
@Mapper
public interface DochubKnowledgeTopicNodeMapper extends BaseMapper<DochubKnowledgeTopicNode> {
    @Select("SELECT * FROM dochub_knowledge_topic_node WHERE scope_code=#{scopeCode} AND status=1 ORDER BY sort_order,id")
    List<DochubKnowledgeTopicNode> selectActiveByScope(@Param("scopeCode") String scopeCode);

    @Select("SELECT * FROM dochub_knowledge_topic_node WHERE scope_code=#{scopeCode} AND canonical_key=#{canonicalKey} AND status=1 LIMIT 1")
    DochubKnowledgeTopicNode selectActiveByCanonicalKey(@Param("scopeCode") String scopeCode,
                                                        @Param("canonicalKey") String canonicalKey);

    @Update("UPDATE dochub_knowledge_topic_node SET scope_code=#{targetScopeCode}, edit_time=NOW() " +
        "WHERE topic_code=#{topicCode} AND status=1")
    int reassignScope(@Param("topicCode") String topicCode, @Param("targetScopeCode") String targetScopeCode);

    @Update("UPDATE dochub_knowledge_topic_node SET status=0, " +
        "canonical_key=LEFT(CONCAT(canonical_key,'#merged#',topic_code),191), edit_time=NOW() " +
        "WHERE topic_code=#{topicCode} AND status=1")
    int deactivateByTopicCode(@Param("topicCode") String topicCode);

    @Select("SELECT GET_LOCK(CONCAT('dochub:topic:', #{scopeCode}, ':', #{canonicalKey}), 5)")
    Integer acquireCanonicalLock(@Param("scopeCode") String scopeCode, @Param("canonicalKey") String canonicalKey);

    @Select("SELECT RELEASE_LOCK(CONCAT('dochub:topic:', #{scopeCode}, ':', #{canonicalKey}))")
    Integer releaseCanonicalLock(@Param("scopeCode") String scopeCode, @Param("canonicalKey") String canonicalKey);
}
