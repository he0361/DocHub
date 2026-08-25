package com.dochub.workbench.docgen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.docgen.data.DocTemplateEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文枢 DocHub 文档模板 Mapper。
 */
@Mapper
public interface DocTemplateMapper extends BaseMapper<DocTemplateEntity> {
}
