CREATE TABLE IF NOT EXISTS dochub_embedding_model_migration (
 id BIGINT NOT NULL PRIMARY KEY, source_config_version BIGINT NOT NULL, target_config_version BIGINT NOT NULL,
 source_model_name VARCHAR(255) NOT NULL, target_model_name VARCHAR(255) NOT NULL,
 source_dimension INT NOT NULL, target_dimension INT NOT NULL,
 source_document_collection VARCHAR(255) NOT NULL, target_document_collection VARCHAR(255) NOT NULL,
 source_memory_collection VARCHAR(255) NOT NULL, target_memory_collection VARCHAR(255) NOT NULL,
 migration_status VARCHAR(32) NOT NULL, document_total BIGINT DEFAULT 0, document_processed BIGINT DEFAULT 0,
 document_failed BIGINT DEFAULT 0, memory_total BIGINT DEFAULT 0, memory_processed BIGINT DEFAULT 0,
 memory_failed BIGINT DEFAULT 0, last_document_chunk_id BIGINT DEFAULT 0, last_memory_summary_id BIGINT DEFAULT 0,
 last_delta_sequence BIGINT DEFAULT 0, lease_owner VARCHAR(128), lease_expire_time DATETIME,
 error_summary VARCHAR(1024), start_time DATETIME, switch_time DATETIME, finish_time DATETIME,
 operator BIGINT, lock_version INT DEFAULT 0, create_time DATETIME, edit_time DATETIME, status TINYINT DEFAULT 1,
 KEY idx_embedding_migration_status (migration_status, status), KEY idx_embedding_migration_target (target_config_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dochub_embedding_migration_delta (
 id BIGINT NOT NULL PRIMARY KEY, migration_id BIGINT NOT NULL, resource_type VARCHAR(16) NOT NULL,
 resource_id BIGINT NOT NULL, operation VARCHAR(16) NOT NULL, sequence_no BIGINT NOT NULL,
 delta_status VARCHAR(16) NOT NULL DEFAULT 'PENDING', attempts INT DEFAULT 0, error_summary VARCHAR(1024),
 create_time DATETIME, edit_time DATETIME, status TINYINT DEFAULT 1,
 UNIQUE KEY uk_embedding_delta_sequence (migration_id, sequence_no),
 KEY idx_embedding_delta_pending (migration_id, delta_status, sequence_no),
 KEY idx_embedding_delta_resource (migration_id, resource_type, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS dochub_embedding_migration_lock (
 lock_name VARCHAR(64) NOT NULL PRIMARY KEY, holder_migration_id BIGINT, edit_time DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT IGNORE INTO dochub_embedding_migration_lock(lock_name, edit_time) VALUES ('embedding-model', NOW());
