package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.manage.data.DochubDocumentStructureNode;
import com.dochub.workbench.manage.mapper.DochubDocumentStructureNodeMapper;
import com.dochub.workbench.manage.service.DocumentStructureNodeService;
import com.dochub.workbench.manage.support.DocumentStructureNodeCandidate;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@AllArgsConstructor
@Service
public class DocumentStructureNodeServiceImpl implements DocumentStructureNodeService {

    private final DochubDocumentStructureNodeMapper structureNodeMapper;
    private final UidGenerator uidGenerator;

    @Override
    public List<DochubDocumentStructureNode> replaceDocumentNodes(Long documentId,
                                                                      Long parseTaskId,
                                                                      List<DocumentStructureNodeCandidate> candidates) {
        deleteByDocumentId(documentId);
        if (documentId == null || parseTaskId == null || candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<Integer, Long> nodeIdMap = new LinkedHashMap<>();
        List<DochubDocumentStructureNode> entities = new ArrayList<>();
        for (DocumentStructureNodeCandidate candidate : candidates) {
            if (candidate == null || candidate.getNodeNo() == null) {
                continue;
            }
            long id = uidGenerator.getUid();
            nodeIdMap.put(candidate.getNodeNo(), id);
        }
        for (DocumentStructureNodeCandidate candidate : candidates) {
            if (candidate == null || candidate.getNodeNo() == null) {
                continue;
            }
            DochubDocumentStructureNode entity = new DochubDocumentStructureNode();
            entity.setId(nodeIdMap.get(candidate.getNodeNo()));
            entity.setDocumentId(documentId);
            entity.setParseTaskId(parseTaskId);
            entity.setNodeNo(candidate.getNodeNo());
            entity.setNodeType(candidate.getNodeType());
            entity.setParentNodeId(candidate.getParentNodeNo() == null ? null : nodeIdMap.get(candidate.getParentNodeNo()));
            entity.setPrevSiblingNodeId(candidate.getPrevSiblingNodeNo() == null ? null : nodeIdMap.get(candidate.getPrevSiblingNodeNo()));
            entity.setNextSiblingNodeId(candidate.getNextSiblingNodeNo() == null ? null : nodeIdMap.get(candidate.getNextSiblingNodeNo()));
            entity.setDepth(candidate.getDepth());
            entity.setNodeCode(candidate.getNodeCode());
            entity.setTitle(candidate.getTitle());
            entity.setAnchorText(candidate.getAnchorText());
            entity.setCanonicalPath(candidate.getCanonicalPath());
            entity.setSectionPath(candidate.getSectionPath());
            entity.setContentText(candidate.getContentText());
            entity.setItemIndex(candidate.getItemIndex());
            entity.setStatus(BusinessStatus.YES.getCode());
            structureNodeMapper.insert(entity);
            entities.add(entity);
        }
        return entities;
    }

    @Override
    public List<DochubDocumentStructureNode> listDocumentNodes(Long documentId, Long parseTaskId) {
        if (documentId == null) {
            return List.of();
        }
        LambdaQueryWrapper<DochubDocumentStructureNode> wrapper = new LambdaQueryWrapper<DochubDocumentStructureNode>()
            .eq(DochubDocumentStructureNode::getDocumentId, documentId)
            .eq(DochubDocumentStructureNode::getStatus, BusinessStatus.YES.getCode())
            .orderByAsc(DochubDocumentStructureNode::getNodeNo);
        if (parseTaskId != null) {
            wrapper.eq(DochubDocumentStructureNode::getParseTaskId, parseTaskId);
        }
        return structureNodeMapper.selectList(wrapper);
    }

    @Override
    public Map<Long, DochubDocumentStructureNode> nodeMap(Long documentId, Long parseTaskId) {
        Map<Long, DochubDocumentStructureNode> result = new LinkedHashMap<>();
        for (DochubDocumentStructureNode node : listDocumentNodes(documentId, parseTaskId)) {
            result.put(node.getId(), node);
        }
        return result;
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId == null) {
            return;
        }
        structureNodeMapper.delete(new LambdaQueryWrapper<DochubDocumentStructureNode>()
            .eq(DochubDocumentStructureNode::getDocumentId, documentId));
    }
}
