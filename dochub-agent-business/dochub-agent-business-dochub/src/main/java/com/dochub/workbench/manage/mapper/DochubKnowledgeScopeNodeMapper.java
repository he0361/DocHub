package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.dochub.workbench.manage.data.DochubKnowledgeScopeNode;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/
@Mapper
public interface DochubKnowledgeScopeNodeMapper extends BaseMapper<DochubKnowledgeScopeNode> {
    @Select("SELECT GET_LOCK(CONCAT('dochub:scope:', #{canonicalKey}), 5)")
    Integer acquireCanonicalLock(@Param("canonicalKey") String canonicalKey);

    @Select("SELECT RELEASE_LOCK(CONCAT('dochub:scope:', #{canonicalKey}))")
    Integer releaseCanonicalLock(@Param("canonicalKey") String canonicalKey);
}
