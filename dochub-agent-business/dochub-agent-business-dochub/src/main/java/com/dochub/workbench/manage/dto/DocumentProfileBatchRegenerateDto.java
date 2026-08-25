package com.dochub.workbench.manage.dto;

import lombok.Data;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 数据传输对象
 * @author: zhangjihe
 **/
@Data
public class DocumentProfileBatchRegenerateDto {

    private List<String> documentIds;

    private String operatorId;
}
