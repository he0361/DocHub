package com.dochub.workbench.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 会话编号项锚点
 * @author: zhangjihe
 **/

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationItemAnchor {

    private Integer itemIndex;

    private String itemText;

    private Long structureNodeId;

    private String canonicalPath;

    public boolean isEmpty() {
        return itemIndex == null
            && (itemText == null || itemText.isBlank())
            && structureNodeId == null
            && (canonicalPath == null || canonicalPath.isBlank());
    }
}
