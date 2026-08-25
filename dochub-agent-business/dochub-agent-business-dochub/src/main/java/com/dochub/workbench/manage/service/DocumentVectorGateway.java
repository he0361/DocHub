package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.data.DochubDocumentChunk;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentVectorGateway {

    void vectorize(List<DochubDocumentChunk> chunkList);

    void deleteByDocumentId(Long documentId);
}
