package com.dochub.workbench.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 视图对象
 * @author: zhangjihe
 **/

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentUploadVo {

    private Long documentId;

    private Long taskId;

    private String documentName;

    private Integer parseStatus;

    private Integer strategyStatus;

    private Integer indexStatus;
}
