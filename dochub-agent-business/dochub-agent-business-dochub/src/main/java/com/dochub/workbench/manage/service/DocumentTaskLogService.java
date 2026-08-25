package com.dochub.workbench.manage.service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务层
 * @author: zhangjihe
 **/

public interface DocumentTaskLogService {

    void saveLog(Long taskId,
                 Long documentId,
                 Integer stageType,
                 Integer eventType,
                 Integer logLevel,
                 Integer operatorType,
                 Long operatorId,
                 String content,
                 Object detail);
}
