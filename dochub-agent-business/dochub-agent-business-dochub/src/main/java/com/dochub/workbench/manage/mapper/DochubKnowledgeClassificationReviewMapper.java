package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.manage.data.DochubKnowledgeClassificationReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DochubKnowledgeClassificationReviewMapper extends BaseMapper<DochubKnowledgeClassificationReview> {
    @Update("UPDATE dochub_knowledge_classification_review SET review_status='RESOLVED', selected_scope_code=#{scopeCode}, " +
        "selected_topic_code=#{topicCode}, trust_llm=#{trustLlm}, operator=#{operator}, version=version+1, edit_time=NOW() " +
        "WHERE id=#{id} AND version=#{version} AND review_status='PENDING' AND status=1")
    int resolvePending(@Param("id") Long id, @Param("version") Integer version,
                       @Param("scopeCode") String scopeCode, @Param("topicCode") String topicCode,
                       @Param("trustLlm") Integer trustLlm, @Param("operator") String operator);
}
