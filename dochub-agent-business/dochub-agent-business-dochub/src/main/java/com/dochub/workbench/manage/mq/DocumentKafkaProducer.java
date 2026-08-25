package com.dochub.workbench.manage.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import com.dochub.workbench.manage.config.DocumentManageProperties;
import com.dochub.workbench.manage.mq.message.DocumentIndexBuildMessage;
import com.dochub.workbench.manage.mq.message.DocumentParseRouteMessage;
import org.javaup.core.SpringUtil;
import org.javaup.enums.DocumentManageCode;
import org.javaup.exception.DochubFrameException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 消息组件
 * @author: zhangjihe
 **/

@AllArgsConstructor
@Component
public class DocumentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper;

    private final DocumentManageProperties properties;

    public void sendParseRoute(DocumentParseRouteMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getParseTopic(),
                String.valueOf(message.getDocumentId()), message);
    }

    public void sendIndexBuild(DocumentIndexBuildMessage message) {

        send(SpringUtil.getPrefixDistinctionName() + "-" + properties.getKafka().getIndexTopic(), String.valueOf(message.getDocumentId()), message);
    }

    private void send(String topic, String key, Object message) {
        try {

            String payload = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, key, payload).get();
        } catch (Exception exception) {
            throw new DochubFrameException(DocumentManageCode.KAFKA_SEND_FAILED.getCode(),
                "Kafka 消息发送失败: " + exception.getMessage(), exception);
        }
    }
}
