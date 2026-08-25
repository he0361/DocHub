package com.dochub.workbench.manage.model.es;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 文档导航索引记录
 * @author: zhangjihe
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentNavigationIndexRecord {

    private Long nodeId;

    private Long documentId;

    private Long parseTaskId;

    private String nodeType;

    private String nodeCode;

    private Integer nodeNo;

    private Integer depth;

    private Long parentNodeId;

    private String title;

    private String anchorText;

    private String sectionPath;

    private String canonicalPath;

    private String contentText;

    private Integer itemIndex;
}
