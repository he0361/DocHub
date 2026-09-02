-- Existing-install migration. Run collision reports first; this script deliberately does not merge rows.
ALTER TABLE dochub_document
    ADD COLUMN classification_status varchar(32) NOT NULL DEFAULT 'UNCLASSIFIED' AFTER document_tags,
    ADD COLUMN classification_review_id bigint DEFAULT NULL AFTER classification_status,
    ADD KEY idx_classification_status (classification_status);

UPDATE dochub_document
SET classification_status = 'CONFIRMED'
WHERE status = 1 AND knowledge_scope_code IS NOT NULL AND knowledge_scope_code <> '';

ALTER TABLE dochub_knowledge_scope_node
    ADD COLUMN canonical_key varchar(191) DEFAULT NULL AFTER scope_name;
ALTER TABLE dochub_knowledge_topic_node
    ADD COLUMN canonical_key varchar(191) DEFAULT NULL AFTER topic_name;

-- Conservative SQL backfill. Application writes the stronger Unicode NFKC form for all new/edited rows.
UPDATE dochub_knowledge_scope_node
SET canonical_key = LOWER(REPLACE(REPLACE(REPLACE(TRIM(scope_name), ' ', ''), '-', ''), '_', ''))
WHERE canonical_key IS NULL;
UPDATE dochub_knowledge_topic_node
SET canonical_key = LOWER(REPLACE(REPLACE(REPLACE(TRIM(topic_name), ' ', ''), '-', ''), '_', ''))
WHERE canonical_key IS NULL;

SELECT canonical_key, COUNT(*) AS collision_count
FROM dochub_knowledge_scope_node WHERE status = 1 GROUP BY canonical_key HAVING COUNT(*) > 1;
SELECT scope_code, canonical_key, COUNT(*) AS collision_count
FROM dochub_knowledge_topic_node WHERE status = 1 GROUP BY scope_code, canonical_key HAVING COUNT(*) > 1;

CREATE TABLE IF NOT EXISTS `dochub_knowledge_classification_review` (
    `id` bigint NOT NULL,
    `document_id` bigint NOT NULL,
    `profile_version` int NOT NULL,
    `review_status` varchar(32) NOT NULL COMMENT 'PENDING/RESOLVED/APPLIED/FAILED',
    `decision_type` varchar(32) NOT NULL,
    `proposed_scope_json` json DEFAULT NULL,
    `proposed_topic_json` json DEFAULT NULL,
    `candidate_json` json NOT NULL,
    `evidence_json` json NOT NULL,
    `reason` varchar(2000) DEFAULT NULL,
    `selected_scope_code` varchar(64) DEFAULT NULL,
    `selected_topic_code` varchar(64) DEFAULT NULL,
    `trust_llm` tinyint(1) NOT NULL DEFAULT '0',
    `operator` varchar(128) DEFAULT NULL,
    `version` int NOT NULL DEFAULT '1',
    `create_time` datetime DEFAULT NULL,
    `edit_time` datetime DEFAULT NULL,
    `status` tinyint(1) DEFAULT '1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_classification_document_profile` (`document_id`, `profile_version`),
    KEY `idx_classification_review_status` (`review_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识分类决策证据与人工审核';

CREATE TABLE IF NOT EXISTS `dochub_knowledge_scope_merge_audit` (
    `id` bigint NOT NULL, `source_scope_code` varchar(64) NOT NULL, `target_scope_code` varchar(64) NOT NULL,
    `document_count` int NOT NULL DEFAULT 0, `topic_count` int NOT NULL DEFAULT 0, `relation_count` int NOT NULL DEFAULT 0,
    `operator` varchar(128) NOT NULL, `detail_json` json DEFAULT NULL, `create_time` datetime DEFAULT NULL,
    `edit_time` datetime DEFAULT NULL, `status` tinyint(1) DEFAULT 1, PRIMARY KEY (`id`),
    KEY `idx_scope_merge_source` (`source_scope_code`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='重复知识域合并审计';
