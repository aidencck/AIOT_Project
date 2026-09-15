CREATE TABLE IF NOT EXISTS ai_mysql_write_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    record_key VARCHAR(64) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    last_error VARCHAR(256) DEFAULT NULL,
    failed_at BIGINT NOT NULL,
    last_retry_at BIGINT DEFAULT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_ai_mysql_write_outbox_task_id (task_id),
    KEY idx_ai_mysql_write_outbox_failed_retry (failed_at, retry_count)
);

CREATE TABLE IF NOT EXISTS ai_case_materialization_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    diagnosis_id VARCHAR(64) NOT NULL,
    feedback_id VARCHAR(64) NOT NULL,
    feedback_type VARCHAR(32) DEFAULT NULL,
    resolution_status VARCHAR(32) DEFAULT NULL,
    resolution_note VARCHAR(1000) DEFAULT NULL,
    operator_id VARCHAR(64) DEFAULT NULL,
    queued_at BIGINT NOT NULL,
    last_retry_at BIGINT DEFAULT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(256) DEFAULT NULL,
    UNIQUE KEY uk_ai_case_materialization_task_task_id (task_id),
    KEY idx_ai_case_materialization_task_queued_retry (queued_at, retry_count),
    KEY idx_ai_case_materialization_task_feedback_id (feedback_id)
);
