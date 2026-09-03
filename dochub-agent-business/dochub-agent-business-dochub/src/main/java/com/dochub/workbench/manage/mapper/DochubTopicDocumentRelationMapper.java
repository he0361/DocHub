package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import com.dochub.workbench.manage.data.DochubTopicDocumentRelation;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/
@Mapper
public interface DochubTopicDocumentRelationMapper extends BaseMapper<DochubTopicDocumentRelation> {
    @Update("UPDATE dochub_topic_document_relation SET status=0, edit_time=NOW() " +
        "WHERE document_id=#{documentId} AND status=1")
    int deactivateByDocumentId(@Param("documentId") Long documentId);

    @Select("SELECT COUNT(*) FROM dochub_topic_document_relation r " +
        "JOIN dochub_knowledge_topic_node t ON t.topic_code=r.topic_code AND t.status=1 " +
        "WHERE t.scope_code=#{scopeCode} AND r.status=1")
    int countActiveByScopeCode(@Param("scopeCode") String scopeCode);

    @Update("UPDATE dochub_topic_document_relation target_relation " +
        "JOIN dochub_topic_document_relation source_relation " +
        "ON source_relation.document_id=target_relation.document_id " +
        "AND source_relation.topic_code=#{sourceTopicCode} AND source_relation.status=1 " +
        "SET target_relation.relation_score=GREATEST(COALESCE(target_relation.relation_score,0), " +
        "COALESCE(source_relation.relation_score,0)), " +
        "target_relation.relation_source=IF(target_relation.status=0,source_relation.relation_source,target_relation.relation_source), " +
        "target_relation.reason=IF(target_relation.status=0,source_relation.reason,target_relation.reason), " +
        "target_relation.status=1, target_relation.edit_time=NOW() " +
        "WHERE target_relation.topic_code=#{targetTopicCode}")
    int restoreCanonicalRelations(@Param("sourceTopicCode") String sourceTopicCode,
                                  @Param("targetTopicCode") String targetTopicCode);

    @Update("UPDATE IGNORE dochub_topic_document_relation SET topic_code=#{targetTopicCode}, edit_time=NOW() " +
        "WHERE topic_code=#{sourceTopicCode} AND status=1")
    int moveActiveRelations(@Param("sourceTopicCode") String sourceTopicCode,
                            @Param("targetTopicCode") String targetTopicCode);

    @Update("UPDATE dochub_topic_document_relation SET status=0, edit_time=NOW() " +
        "WHERE topic_code=#{topicCode} AND status=1")
    int deactivateByTopicCode(@Param("topicCode") String topicCode);
}
