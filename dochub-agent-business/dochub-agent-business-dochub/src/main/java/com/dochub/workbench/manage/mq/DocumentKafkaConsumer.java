package com.dochub.workbench.manage.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.dochub.workbench.manage.mq.message.DocumentIndexBuildMessage;
import com.dochub.workbench.manage.mq.message.DocumentParseRouteMessage;
import com.dochub.workbench.manage.service.DocumentAsyncProcessService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static org.javaup.constant.Constant.SPRING_INJECT_PREFIX_DISTINCTION_NAME;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 消息组件
 * @author: zhangjihe
 **/

@Slf4j
@Component
public class DocumentKafkaConsumer {

    private final DocumentAsyncProcessService asyncProcessService;

    private final ObjectMapper objectMapper;

    public DocumentKafkaConsumer(DocumentAsyncProcessService asyncProcessService,
                                 ObjectMapper objectMapper) {
        this.asyncProcessService = asyncProcessService;
        this.objectMapper = objectMapper;
    }
@KafkaListener(topics = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${app.manage.kafka.parse-topic}",groupId = "${app.manage.kafka.group-id}-parse")
public void consumeParseRoute(String payload){
        try{
            //先把JSON还原成强类型消息对象，避免后续处理层直接面对原始字符串。
            DocumentParseRouteMessage message=objectMapper.readValue(payload,DocumentParseRouteMessage.class);
            //真正的业务推进放到异步处理服务中，这里只承担“消费并转发”的职责
            asyncProcessService.handleParseRoute(message.getDocumentId(),message.getTaskId());
        }catch (Exception e){
            //消费失败只记录日志，不让异常继续向外冒泡破坏监听线程
            log.error("消费解析路由消息失败，payload={}",payload,e);
        }
}

    @KafkaListener(topics = SPRING_INJECT_PREFIX_DISTINCTION_NAME+"-"+"${app.manage.kafka.index-topic}", groupId = "${app.manage.kafka.group-id}-index")
    public void consumeIndexBuild(String payload) {
        try {

            DocumentIndexBuildMessage message = objectMapper.readValue(payload, DocumentIndexBuildMessage.class);

            asyncProcessService.handleIndexBuild(message.getDocumentId(), message.getTaskId(), message.getPlanId());
        }
        catch (Exception exception) {
            log.error("消费索引构建消息失败，payload={}", payload, exception);
        }
    }
}
