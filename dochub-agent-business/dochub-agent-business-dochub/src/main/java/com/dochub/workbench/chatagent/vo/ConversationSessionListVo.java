package com.dochub.workbench.chatagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.dochub.workbench.chatagent.model.ConversationSessionView;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionListVo {

    private long pageNo;

    private long pageSize;

    private long totalSize;

    private long totalPages;

    private List<ConversationSessionView> sessions;
}
