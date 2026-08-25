package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.dto.DocumentProfileBatchRegenerateDto;
import com.dochub.workbench.manage.dto.DocumentProfileDetailQueryDto;
import com.dochub.workbench.manage.dto.DocumentProfileRegenerateDto;
import com.dochub.workbench.manage.dto.KnowledgeRouteTraceQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeScopeDeleteDto;
import com.dochub.workbench.manage.dto.KnowledgeScopeSaveDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicDeleteDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicQueryDto;
import com.dochub.workbench.manage.dto.KnowledgeTopicSaveDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationListQueryDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationRemoveDto;
import com.dochub.workbench.manage.dto.TopicDocumentRelationSaveDto;
import com.dochub.workbench.manage.vo.DocumentProfileVo;
import com.dochub.workbench.manage.vo.KnowledgeRouteTracePageVo;
import com.dochub.workbench.manage.vo.KnowledgeScopeItemVo;
import com.dochub.workbench.manage.vo.KnowledgeTopicItemVo;
import com.dochub.workbench.manage.vo.TopicDocumentRelationItemVo;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/
public interface KnowledgeManageService {

    KnowledgeScopeItemVo saveScope(KnowledgeScopeSaveDto dto);

    boolean deleteScope(KnowledgeScopeDeleteDto dto);

    List<KnowledgeScopeItemVo> listScopes();

    KnowledgeTopicItemVo saveTopic(KnowledgeTopicSaveDto dto);

    boolean deleteTopic(KnowledgeTopicDeleteDto dto);

    List<KnowledgeTopicItemVo> listTopics(KnowledgeTopicQueryDto dto);

    DocumentProfileVo queryProfile(DocumentProfileDetailQueryDto dto);

    DocumentProfileVo regenerateProfile(DocumentProfileRegenerateDto dto);

    List<DocumentProfileVo> batchRegenerateProfiles(DocumentProfileBatchRegenerateDto dto);

    List<TopicDocumentRelationItemVo> listTopicDocuments(TopicDocumentRelationListQueryDto dto);

    TopicDocumentRelationItemVo saveTopicDocumentRelation(TopicDocumentRelationSaveDto dto);

    boolean removeTopicDocumentRelation(TopicDocumentRelationRemoveDto dto);

    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTraceQueryDto dto);
}
