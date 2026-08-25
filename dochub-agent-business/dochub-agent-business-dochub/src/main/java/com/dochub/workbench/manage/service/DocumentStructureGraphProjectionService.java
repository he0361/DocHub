package com.dochub.workbench.manage.service;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentStructureGraphProjectionService {

    boolean enabled();

    void projectToGraph(Long documentId, Long parseTaskId);

    void deleteByDocumentId(Long documentId);

    default List<String> statusNotes() {
        return List.of();
    }
}
