package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
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
}
