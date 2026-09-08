CREATE TABLE IF NOT EXISTS `dochub_ai_model_config` (
    `id` bigint NOT NULL, `model_type` varchar(16) NOT NULL, `deployment_type` varchar(16) NOT NULL,
    `compatibility_preset` varchar(64) NOT NULL, `base_url` varchar(1024) NOT NULL,
    `request_path` varchar(512) DEFAULT NULL, `model_name` varchar(255) NOT NULL,
    `encrypted_api_key` text, `temperature` decimal(4,3) DEFAULT NULL, `max_tokens` int DEFAULT NULL,
    `timeout_millis` int NOT NULL, `tool_calling_supported` tinyint(1) NOT NULL DEFAULT '0',
    `options_json` json DEFAULT NULL, `config_version` bigint NOT NULL, `active` tinyint(1) NOT NULL DEFAULT '0',
    `active_model_type` varchar(16) GENERATED ALWAYS AS (CASE WHEN `active` = 1 THEN `model_type` ELSE NULL END) STORED,
    `updated_by` bigint DEFAULT NULL, `create_time` datetime DEFAULT NULL, `edit_time` datetime DEFAULT NULL,
    `status` tinyint(1) DEFAULT '1', PRIMARY KEY (`id`),
    UNIQUE KEY `uk_model_type_config_version` (`model_type`, `config_version`),
    UNIQUE KEY `uk_active_model_type` (`active_model_type`),
    KEY `idx_model_type_active` (`model_type`, `active`), KEY `idx_model_config_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `dochub_ai_model_config_audit` (
    `id` bigint NOT NULL, `model_config_id` bigint DEFAULT NULL, `model_type` varchar(16) DEFAULT NULL,
    `config_version` bigint DEFAULT NULL, `action` varchar(32) NOT NULL, `success` tinyint(1) NOT NULL,
    `masked_endpoint` varchar(1024) DEFAULT NULL, `operator` bigint DEFAULT NULL, `error` text,
    `create_time` datetime DEFAULT NULL, PRIMARY KEY (`id`),
    KEY `idx_model_config_audit_config` (`model_config_id`),
    KEY `idx_model_config_audit_type_version` (`model_type`, `config_version`),
    KEY `idx_model_config_audit_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `dochub_embedding_model_migration` (
    `id` bigint NOT NULL PRIMARY KEY, `source_config_version` bigint NOT NULL, `target_config_version` bigint NOT NULL,
    `source_model_name` varchar(255) NOT NULL, `target_model_name` varchar(255) NOT NULL,
    `source_dimension` int NOT NULL, `target_dimension` int NOT NULL,
    `source_document_collection` varchar(255) NOT NULL, `target_document_collection` varchar(255) NOT NULL,
    `source_memory_collection` varchar(255) NOT NULL, `target_memory_collection` varchar(255) NOT NULL,
    `migration_status` varchar(32) NOT NULL, `resume_status` varchar(32), `document_total` bigint DEFAULT 0,
    `document_processed` bigint DEFAULT 0, `document_failed` bigint DEFAULT 0, `memory_total` bigint DEFAULT 0,
    `memory_processed` bigint DEFAULT 0, `memory_failed` bigint DEFAULT 0, `last_document_chunk_id` bigint DEFAULT 0,
    `last_memory_summary_id` bigint DEFAULT 0, `last_delta_sequence` bigint DEFAULT 0,
    `active_mutations` int NOT NULL DEFAULT 0, `lease_owner` varchar(128), `lease_expire_time` datetime,
    `error_summary` varchar(1024), `start_time` datetime, `switch_time` datetime, `finish_time` datetime,
    `operator` bigint, `lock_version` int DEFAULT 0, `create_time` datetime, `edit_time` datetime,
    `status` tinyint DEFAULT 1, KEY `idx_embedding_migration_status` (`migration_status`, `status`),
    KEY `idx_embedding_migration_target` (`target_config_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `dochub_embedding_migration_delta` (
    `id` bigint NOT NULL PRIMARY KEY, `migration_id` bigint NOT NULL, `resource_type` varchar(16) NOT NULL,
    `resource_id` bigint NOT NULL, `operation` varchar(16) NOT NULL, `sequence_no` bigint NOT NULL,
    `delta_status` varchar(16) NOT NULL DEFAULT 'PENDING', `attempts` int DEFAULT 0, `error_summary` varchar(1024),
    `create_time` datetime, `edit_time` datetime, `status` tinyint DEFAULT 1,
    UNIQUE KEY `uk_embedding_delta_sequence` (`migration_id`, `sequence_no`),
    KEY `idx_embedding_delta_pending` (`migration_id`, `delta_status`, `sequence_no`),
    KEY `idx_embedding_delta_resource` (`migration_id`, `resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `dochub_embedding_migration_lock` (
    `lock_name` varchar(64) NOT NULL PRIMARY KEY, `holder_migration_id` bigint, `edit_time` datetime
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO `dochub_embedding_migration_lock` (`lock_name`, `edit_time`) VALUES ('embedding-model', NOW());
