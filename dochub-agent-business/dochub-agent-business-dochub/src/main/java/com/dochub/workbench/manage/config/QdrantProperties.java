package com.dochub.workbench.manage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文枢 DocHub 向量数据库 Qdrant 配置（app.manage.qdrant.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.manage.qdrant")
public class QdrantProperties {

    private String host = "127.0.0.1";

    private int port = 6333;

    /** 文档向量 collection */
    private String documentCollection = "dochub_document";

    /** 会话长期记忆向量 collection */
    private String memoryCollection = "dochub_conversation_memory";
}
