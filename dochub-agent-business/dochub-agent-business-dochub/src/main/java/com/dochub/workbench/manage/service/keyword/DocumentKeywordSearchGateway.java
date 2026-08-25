package com.dochub.workbench.manage.service.keyword;

import com.dochub.workbench.manage.data.DochubDocumentChunk;
import com.dochub.workbench.manage.model.DocumentRetrieveRequest;
import org.springframework.ai.document.Document;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentKeywordSearchGateway {

    void indexChunks(List<DochubDocumentChunk> chunkList);

    List<Document> search(DocumentRetrieveRequest request);

    void deleteByDocumentId(Long documentId);
}
