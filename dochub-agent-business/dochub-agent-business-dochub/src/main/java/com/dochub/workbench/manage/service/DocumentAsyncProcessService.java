package com.dochub.workbench.manage.service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentAsyncProcessService {

    void handleParseRoute(Long documentId, Long taskId);

    void handleIndexBuild(Long documentId, Long taskId, Long planId);
}
