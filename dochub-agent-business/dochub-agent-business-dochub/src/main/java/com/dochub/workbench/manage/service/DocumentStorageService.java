package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.support.StoredObjectInfo;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentStorageService {

    StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, byte[] bytes, String contentType);

    String uploadParsedText(Long documentId, String parsedText);

    byte[] downloadObject(String objectName);

    String downloadText(String objectName);

    void deleteObjects(List<String> objectNameList);
}
