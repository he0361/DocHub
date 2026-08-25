package com.dochub.workbench.manage.service;

import org.javaup.enums.DocumentFileTypeEnum;
import com.dochub.workbench.manage.support.DocumentAnalysisResult;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentParserService {

    DocumentAnalysisResult parse(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType);

    /**
     * 仅提取清洗后的纯文本（跳过结构节点抽取与 LLM 消歧）。
     * 适用于只需要正文、不需要章节结构的场景（如 docgen 参考文档仿写），避免不必要的耗时。
     */
    String parseTextOnly(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType);
}
