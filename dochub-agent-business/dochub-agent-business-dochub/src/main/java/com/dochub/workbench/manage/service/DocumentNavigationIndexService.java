package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocumentStructureNode;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentNavigationIndexService {

    void reindexDocumentNodes(Long documentId, Long parseTaskId, List<DochubDocumentStructureNode> nodes);

    void deleteByDocumentId(Long documentId);

    List<NavigationSectionHit> searchSections(Long documentId,
                                              String topic,
                                              String facet,
                                              String informationNeed,
                                              String question,
                                              int size);

    record NavigationSectionHit(
        Long nodeId,
        String nodeCode,
        String title,
        String sectionPath,
        String canonicalPath,
        double score
    ) {
    }
}
