package com.dochub.workbench.manage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 zhangjihe 微信
 * @description: 配置属性
 * @author: zhangjihe
 **/

@Data
@ConfigurationProperties(prefix = "app.manage")
public class DocumentManageProperties {

    private Minio minio = new Minio();

    private Kafka kafka = new Kafka();

    private Chunk chunk = new Chunk();

    private StructureParsing structureParsing = new StructureParsing();

    private Elasticsearch elasticsearch = new Elasticsearch();

    private Neo4j neo4j = new Neo4j();

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "dochub-document";
        private String objectPrefix = "rag/document";
        private String parsedTextPrefix = "rag/parsed-text";
    }

    @Data
    public static class Kafka {
        private String parseTopic = "dochub-document-parse-route";
        private String indexTopic = "dochub-document-index-build";
        private String groupId = "dochub-document-manage";
        private Boolean autoCreateTopics = Boolean.TRUE;
    }

    @Data
    public static class Chunk {
        private Integer recursiveMaxChars = 800;
        private Integer recursiveOverlapChars = 120;
        private Integer semanticMaxChars = 700;
        private Integer semanticMinChars = 240;
        private Double semanticSimilarityThreshold = 0.18D;
        private Boolean llmEnabled = Boolean.FALSE;
        private Integer llmMaxChars = 3500;
        private Boolean recommendLlmWhenLowQuality = Boolean.TRUE;
    }

    @Data
    public static class StructureParsing {

        private Boolean llmDisambiguationEnabled = Boolean.TRUE;

        private Integer maxAmbiguousSignalsPerCall = 8;

        private Integer contextWindowLines = 2;

        private Integer maxPlainHeadingChars = 32;

        private Double ambiguityConfidenceFloor = 0.45D;

        private Double ambiguityConfidenceCeil = 0.80D;
    }

    @Data
    public static class Elasticsearch {

        private Boolean enabled = Boolean.TRUE;

        private List<String> uris = new ArrayList<>(List.of("http://127.0.0.1:9200"));

        private String username = "elastic";

        private String password = "elastic";

        private String indexName = "dochub_document_keyword";

        private String analyzer = "ik_max_word";

        private String searchAnalyzer = "ik_smart";

        private String navigationIndexName = "dochub_document_navigation";

        private String routeIndexName = "dochub_knowledge_route";

        private Integer connectTimeoutMillis = 3000;

        private Integer socketTimeoutMillis = 5000;
    }

    @Data
    public static class Neo4j {

        private Boolean enabled = Boolean.FALSE;

        private String uri = "bolt://127.0.0.1:7687";

        private String username = "neo4j";

        private String password = "neo4j";

        private String database = "neo4j";

        private Integer queryTimeoutSeconds = 5;
    }
}
