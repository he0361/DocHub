package com.dochub.workbench.manage.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Brings databases created by older DocHub releases up to the schema expected by
 * the knowledge classification code. The migration is deliberately idempotent so
 * an existing local or server database can be reused after every application update.
 */
@Component
@DependsOnDatabaseInitialization
public class KnowledgeSchemaMigrationInitializer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSchemaMigrationInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public KnowledgeSchemaMigrationInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void migrate() {
        if (!tableExists("dochub_document")
            || !tableExists("dochub_knowledge_scope_node")
            || !tableExists("dochub_knowledge_topic_node")) {
            log.warn("DocHub business tables are not initialized; skipping knowledge schema upgrade");
            return;
        }

        addColumnIfMissing("dochub_document", "classification_status",
            "ALTER TABLE dochub_document ADD COLUMN classification_status varchar(32) "
                + "NOT NULL DEFAULT 'UNCLASSIFIED' AFTER document_tags");
        addColumnIfMissing("dochub_document", "classification_review_id",
            "ALTER TABLE dochub_document ADD COLUMN classification_review_id bigint DEFAULT NULL "
                + "AFTER classification_status");
        addIndexIfMissing("dochub_document", "idx_classification_status",
            "ALTER TABLE dochub_document ADD KEY idx_classification_status (classification_status)");
        jdbcTemplate.update("UPDATE dochub_document SET classification_status='CONFIRMED' "
            + "WHERE status=1 AND (classification_status IS NULL OR classification_status='UNCLASSIFIED') "
            + "AND knowledge_scope_code IS NOT NULL AND knowledge_scope_code<>''");

        addColumnIfMissing("dochub_knowledge_scope_node", "canonical_key",
            "ALTER TABLE dochub_knowledge_scope_node ADD COLUMN canonical_key varchar(191) DEFAULT NULL "
                + "AFTER scope_name");
        addColumnIfMissing("dochub_knowledge_topic_node", "canonical_key",
            "ALTER TABLE dochub_knowledge_topic_node ADD COLUMN canonical_key varchar(191) DEFAULT NULL "
                + "AFTER topic_name");
        backfillCanonicalKeys();

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dochub_knowledge_classification_review ("
            + "id bigint NOT NULL, document_id bigint NOT NULL, profile_version int NOT NULL, "
            + "review_status varchar(32) NOT NULL, decision_type varchar(32) NOT NULL, "
            + "proposed_scope_json json DEFAULT NULL, proposed_topic_json json DEFAULT NULL, "
            + "candidate_json json NOT NULL, evidence_json json NOT NULL, reason varchar(2000) DEFAULT NULL, "
            + "selected_scope_code varchar(64) DEFAULT NULL, selected_topic_code varchar(64) DEFAULT NULL, "
            + "trust_llm tinyint(1) NOT NULL DEFAULT 0, operator varchar(128) DEFAULT NULL, "
            + "version int NOT NULL DEFAULT 1, create_time datetime DEFAULT NULL, edit_time datetime DEFAULT NULL, "
            + "status tinyint(1) DEFAULT 1, PRIMARY KEY (id), "
            + "UNIQUE KEY uk_classification_document_profile (document_id, profile_version), "
            + "KEY idx_classification_review_status (review_status, create_time)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge classification review'");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS dochub_knowledge_scope_merge_audit ("
            + "id bigint NOT NULL, source_scope_code varchar(64) NOT NULL, target_scope_code varchar(64) NOT NULL, "
            + "document_count int NOT NULL DEFAULT 0, topic_count int NOT NULL DEFAULT 0, "
            + "relation_count int NOT NULL DEFAULT 0, operator varchar(128) NOT NULL, detail_json json DEFAULT NULL, "
            + "create_time datetime DEFAULT NULL, edit_time datetime DEFAULT NULL, status tinyint(1) DEFAULT 1, "
            + "PRIMARY KEY (id), KEY idx_scope_merge_source (source_scope_code, create_time)"
            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Knowledge scope merge audit'");

        finalizeCanonicalUniqueness();
        log.info("Knowledge classification database schema is ready");
    }

    private void backfillCanonicalKeys() {
        jdbcTemplate.update("UPDATE dochub_knowledge_scope_node "
            + "SET canonical_key=LOWER(REPLACE(REPLACE(REPLACE(TRIM(scope_name),' ',''),'-',''),'_','')) "
            + "WHERE canonical_key IS NULL OR canonical_key=''");
        jdbcTemplate.update("UPDATE dochub_knowledge_topic_node "
            + "SET canonical_key=LOWER(REPLACE(REPLACE(REPLACE(TRIM(topic_name),' ',''),'-',''),'_','')) "
            + "WHERE canonical_key IS NULL OR canonical_key=''");
    }

    private void finalizeCanonicalUniqueness() {
        Integer blankScopes = count("SELECT COUNT(*) FROM dochub_knowledge_scope_node "
            + "WHERE canonical_key IS NULL OR canonical_key=''");
        Integer duplicateScopes = count("SELECT COUNT(*) FROM (SELECT canonical_key "
            + "FROM dochub_knowledge_scope_node GROUP BY canonical_key HAVING COUNT(*)>1) c");
        Integer blankTopics = count("SELECT COUNT(*) FROM dochub_knowledge_topic_node "
            + "WHERE canonical_key IS NULL OR canonical_key=''");
        Integer duplicateTopics = count("SELECT COUNT(*) FROM (SELECT scope_code,canonical_key "
            + "FROM dochub_knowledge_topic_node GROUP BY scope_code,canonical_key HAVING COUNT(*)>1) c");

        if (blankScopes > 0 || duplicateScopes > 0 || blankTopics > 0 || duplicateTopics > 0) {
            log.warn("Canonical knowledge keys need administrator review: blankScopes={}, duplicateScopes={}, "
                    + "blankTopics={}, duplicateTopics={}",
                blankScopes, duplicateScopes, blankTopics, duplicateTopics);
            return;
        }

        makeColumnRequired("dochub_knowledge_scope_node", "canonical_key",
            "ALTER TABLE dochub_knowledge_scope_node "
                + "MODIFY COLUMN canonical_key varchar(191) NOT NULL COMMENT 'NFKC normalized unique key'");
        makeColumnRequired("dochub_knowledge_topic_node", "canonical_key",
            "ALTER TABLE dochub_knowledge_topic_node "
                + "MODIFY COLUMN canonical_key varchar(191) NOT NULL COMMENT 'NFKC normalized unique key'");
        addIndexIfMissing("dochub_knowledge_scope_node", "uk_scope_canonical_key",
            "ALTER TABLE dochub_knowledge_scope_node ADD UNIQUE KEY uk_scope_canonical_key (canonical_key)");
        addIndexIfMissing("dochub_knowledge_topic_node", "uk_topic_scope_canonical_key",
            "ALTER TABLE dochub_knowledge_topic_node "
                + "ADD UNIQUE KEY uk_topic_scope_canonical_key (scope_code, canonical_key)");
    }

    private boolean tableExists(String tableName) {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() "
            + "AND table_name='" + tableName + "'") > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        return count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() "
            + "AND table_name='" + tableName + "' AND column_name='" + columnName + "'") > 0;
    }

    private boolean indexExists(String tableName, String indexName) {
        return count("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() "
            + "AND table_name='" + tableName + "' AND index_name='" + indexName + "'") > 0;
    }

    private boolean columnNullable(String tableName, String columnName) {
        String nullable = jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() "
                + "AND table_name=? AND column_name=?",
            String.class, tableName, columnName);
        return "YES".equalsIgnoreCase(nullable);
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        if (!columnExists(tableName, columnName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String ddl) {
        if (!indexExists(tableName, indexName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private void makeColumnRequired(String tableName, String columnName, String ddl) {
        if (columnNullable(tableName, columnName)) {
            jdbcTemplate.execute(ddl);
        }
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
