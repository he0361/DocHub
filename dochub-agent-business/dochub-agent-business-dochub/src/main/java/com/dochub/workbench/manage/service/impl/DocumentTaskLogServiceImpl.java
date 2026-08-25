package com.dochub.workbench.manage.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dochub.workbench.manage.data.DochubDocumentTaskLog;
import com.dochub.workbench.manage.mapper.DochubDocumentTaskLogMapper;
import com.dochub.workbench.manage.service.DocumentTaskLogService;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 服务实现层
 * @author: zhangjihe
 **/

@AllArgsConstructor
@Service
public class DocumentTaskLogServiceImpl implements DocumentTaskLogService {

    private final DochubDocumentTaskLogMapper taskLogMapper;
    private final ObjectMapper objectMapper;
    private final UidGenerator uidGenerator;

    @Override
    public void saveLog(Long taskId,
                        Long documentId,
                        Integer stageType,
                        Integer eventType,
                        Integer logLevel,
                        Integer operatorType,
                        Long operatorId,
                        String content,
                        Object detail) {
        DochubDocumentTaskLog log = new DochubDocumentTaskLog();
        log.setId(uidGenerator.getUid());
        log.setTaskId(taskId);
        log.setDocumentId(documentId);
        log.setStageType(stageType);
        log.setEventType(eventType);
        log.setLogLevel(logLevel);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setContent(content);
        log.setDetailJson(toJson(detail));
        log.setStatus(BusinessStatus.YES.getCode());
        taskLogMapper.insert(log);
    }

    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        }
        catch (JsonProcessingException exception) {
            return String.valueOf(detail);
        }
    }
}
