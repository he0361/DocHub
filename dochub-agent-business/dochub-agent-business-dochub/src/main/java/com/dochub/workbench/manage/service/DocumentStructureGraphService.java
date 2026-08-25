package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.graph.GraphItem;
import com.dochub.workbench.manage.model.graph.GraphSection;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentStructureGraphService {

    default boolean isGraphAvailable(Long documentId) {
        return documentId != null;
    }

    GraphSection findSectionById(Long documentId, Long sectionNodeId);

    GraphSection findSectionByCode(Long documentId, String nodeCode);

    GraphSection findSectionByTitle(Long documentId, String title);

    GraphSection findSectionByCanonicalPath(Long documentId, String canonicalPath);

    GraphSection findBestSection(Long documentId, String topic, String facet);

    List<GraphSection> listSections(Long documentId);

    List<GraphSection> listChildren(Long documentId, Long sectionNodeId);

    GraphSection parentSection(Long documentId, Long sectionNodeId);

    GraphSection previousSibling(Long documentId, Long sectionNodeId);

    GraphSection nextSibling(Long documentId, Long sectionNodeId);

    GraphItem findItemByIndex(Long documentId, Long sectionNodeId, Integer itemIndex);

    List<GraphItem> listItems(Long documentId, Long sectionNodeId);

    List<GraphItem> searchItemsInSection(Long documentId, Long sectionNodeId, String keyword);
}
