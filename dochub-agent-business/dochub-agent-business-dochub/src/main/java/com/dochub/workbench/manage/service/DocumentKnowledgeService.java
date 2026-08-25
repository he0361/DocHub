package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.model.DocumentRetrieveRequest;
import com.dochub.workbench.manage.model.KnowledgeDocumentDescriptor;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentKnowledgeService {

    List<KnowledgeDocumentDescriptor> listRetrievableDocuments();

    List<Document> vectorSearch(DocumentRetrieveRequest request);

    List<Document> keywordSearch(DocumentRetrieveRequest request);

    List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars);
}
