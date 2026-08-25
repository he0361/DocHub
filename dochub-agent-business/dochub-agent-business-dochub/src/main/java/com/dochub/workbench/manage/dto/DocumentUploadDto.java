package com.dochub.workbench.manage.dto;

import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/

@Data
public class DocumentUploadDto {
    private String documentName;//文档展示名称（不传则用原始文件名）

    private String operatorId;//操作人ID

    private String knowledgeScopeCode;//知识域编码

    private String knowledgeScopeName;//知识域名称

    private String businessCategory;//业务分类

    private String documentTags;//文档标签
}
