package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.dochub.workbench.manage.data.DochubKnowledgeTopicNode;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/
@Mapper
public interface DochubKnowledgeTopicNodeMapper extends BaseMapper<DochubKnowledgeTopicNode> {
    @Select("SELECT GET_LOCK(CONCAT('dochub:topic:', #{scopeCode}, ':', #{canonicalKey}), 5)")
    Integer acquireCanonicalLock(@Param("scopeCode") String scopeCode, @Param("canonicalKey") String canonicalKey);

    @Select("SELECT RELEASE_LOCK(CONCAT('dochub:topic:', #{scopeCode}, ':', #{canonicalKey}))")
    Integer releaseCanonicalLock(@Param("scopeCode") String scopeCode, @Param("canonicalKey") String canonicalKey);
}
