package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 会话结构锚点
 * @author: zhangjihe
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationStructureAnchor {

    private String rootSectionCode;

    private String rootSectionTitle;

    private String targetSectionHint;

    private Long structureNodeId;

    private String canonicalPath;

    private String scopeMode;

    public boolean isEmpty() {
        return (rootSectionCode == null || rootSectionCode.isBlank())
            && (rootSectionTitle == null || rootSectionTitle.isBlank())
            && (targetSectionHint == null || targetSectionHint.isBlank())
            && structureNodeId == null
            && (canonicalPath == null || canonicalPath.isBlank());
    }
}
