package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunkQueryVo {

    private Long documentId;

    private Long taskId;

    private Long planId;

    private Integer pageNo;

    private Integer pageSize;

    private Long total;

    private List<DocumentChunkItemVo> records;
}
