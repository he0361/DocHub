package com.dochub.workbench.docgen.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dochub.workbench.docgen.data.DocGenerationRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文枢 DocHub 文档生成历史 Mapper。
 */
@Mapper
public interface DocGenerationRecordMapper extends BaseMapper<DocGenerationRecordEntity> {
}
