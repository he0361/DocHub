-- Run only after reviewing the collision queries in
-- 20260826_add_knowledge_classification_review.sql and merging every duplicate.
-- This migration deliberately aborts instead of silently choosing a winner.

DROP PROCEDURE IF EXISTS finalize_dochub_knowledge_canonical_unique;
DELIMITER $$
CREATE PROCEDURE finalize_dochub_knowledge_canonical_unique()
BEGIN
    DECLARE invalid_count BIGINT DEFAULT 0;
    DECLARE collision_count BIGINT DEFAULT 0;
    DECLARE index_count BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO invalid_count
    FROM dochub_knowledge_scope_node
    WHERE canonical_key IS NULL OR canonical_key = '';
    IF invalid_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Knowledge scope canonical keys are missing; backfill them before finalization';
    END IF;

    SELECT COUNT(*) INTO collision_count
    FROM (
        SELECT canonical_key
        FROM dochub_knowledge_scope_node
        GROUP BY canonical_key
        HAVING COUNT(*) > 1
    ) AS scope_collisions;
    IF collision_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate knowledge scope canonical keys remain; merge them before finalization';
    END IF;

    SELECT COUNT(*) INTO invalid_count
    FROM dochub_knowledge_topic_node
    WHERE canonical_key IS NULL OR canonical_key = '';
    IF invalid_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Knowledge topic canonical keys are missing; backfill them before finalization';
    END IF;

    SELECT COUNT(*) INTO collision_count
    FROM (
        SELECT scope_code, canonical_key
        FROM dochub_knowledge_topic_node
        GROUP BY scope_code, canonical_key
        HAVING COUNT(*) > 1
    ) AS topic_collisions;
    IF collision_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Duplicate knowledge topic canonical keys remain; merge them before finalization';
    END IF;

    ALTER TABLE dochub_knowledge_scope_node
        MODIFY COLUMN canonical_key varchar(191) NOT NULL COMMENT 'NFKC 规范化唯一键';
    ALTER TABLE dochub_knowledge_topic_node
        MODIFY COLUMN canonical_key varchar(191) NOT NULL COMMENT 'NFKC 规范化唯一键';

    SELECT COUNT(*) INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'dochub_knowledge_scope_node'
      AND index_name = 'uk_scope_canonical_key';
    IF index_count = 0 THEN
        ALTER TABLE dochub_knowledge_scope_node
            ADD UNIQUE KEY uk_scope_canonical_key (canonical_key);
    END IF;

    SELECT COUNT(*) INTO index_count
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'dochub_knowledge_topic_node'
      AND index_name = 'uk_topic_scope_canonical_key';
    IF index_count = 0 THEN
        ALTER TABLE dochub_knowledge_topic_node
            ADD UNIQUE KEY uk_topic_scope_canonical_key (scope_code, canonical_key);
    END IF;
END$$
DELIMITER ;

CALL finalize_dochub_knowledge_canonical_unique();
DROP PROCEDURE finalize_dochub_knowledge_canonical_unique;
