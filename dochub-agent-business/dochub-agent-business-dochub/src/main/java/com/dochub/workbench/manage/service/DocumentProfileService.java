package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocumentProfile;
import com.dochub.workbench.manage.data.DochubDocumentStructureNode;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/
public interface DocumentProfileService {

    DochubDocumentProfile generateProfile(Long documentId,
                                              DocumentAnalysisResult analysisResult,
                                              List<DochubDocumentStructureNode> structureNodes);

    DochubDocumentProfile regenerateProfile(Long documentId);

    List<DochubDocumentProfile> batchRegenerateProfiles(Collection<Long> documentIds);

    Optional<DochubDocumentProfile> getByDocumentId(Long documentId);
}
