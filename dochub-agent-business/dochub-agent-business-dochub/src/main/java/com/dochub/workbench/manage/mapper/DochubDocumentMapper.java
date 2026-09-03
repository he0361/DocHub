package com.dochub.workbench.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import com.dochub.workbench.manage.data.DochubDocument;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: Mapper层
 * @author: zhangjihe
 **/

@Mapper
public interface DochubDocumentMapper extends BaseMapper<DochubDocument> {
    @Select("SELECT COUNT(*) FROM dochub_document WHERE knowledge_scope_code=#{scopeCode} AND status=1")
    int countActiveByScopeCode(@Param("scopeCode") String scopeCode);

    @Update("UPDATE dochub_document SET knowledge_scope_code=#{targetCode}, knowledge_scope_name=#{targetName}, edit_time=NOW() " +
        "WHERE knowledge_scope_code=#{sourceCode} AND status=1")
    int replaceScopeCode(@Param("sourceCode") String sourceCode,
                         @Param("targetCode") String targetCode,
                         @Param("targetName") String targetName);
}
