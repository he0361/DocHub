package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocumentStructureNode;
import com.dochub.workbench.manage.support.DocumentStructureNodeCandidate;

import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentStructureNodeService {

    List<DochubDocumentStructureNode> replaceDocumentNodes(Long documentId,
                                                               Long parseTaskId,
                                                               List<DocumentStructureNodeCandidate> candidates);

    List<DochubDocumentStructureNode> listDocumentNodes(Long documentId, Long parseTaskId);

    Map<Long, DochubDocumentStructureNode> nodeMap(Long documentId, Long parseTaskId);

    void deleteByDocumentId(Long documentId);
}
