package com.dochub.workbench.manage.model.graph;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 结构图里的章节节点视图
 * @author: zhangjihe
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSection {

    private Long nodeId;

    private Long documentId;

    private Long parseTaskId;

    private Integer nodeNo;

    private Integer depth;

    private Long parentNodeId;

    private Long prevSiblingNodeId;

    private Long nextSiblingNodeId;

    private String nodeCode;

    private String title;

    private String anchorText;

    private String sectionPath;

    private String canonicalPath;

    private String contentText;

    public String displayTitle() {
        if (StrUtil.isNotBlank(sectionPath)) {
            return sectionPath.trim();
        }
        if (StrUtil.isNotBlank(nodeCode) && StrUtil.isNotBlank(title)) {
            return (nodeCode + " " + title).trim();
        }
        return StrUtil.blankToDefault(title, "");
    }
}
