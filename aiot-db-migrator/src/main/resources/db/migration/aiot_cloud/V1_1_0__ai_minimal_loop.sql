CREATE TABLE IF NOT EXISTS ai_diagnosis_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    diagnosis_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) DEFAULT NULL,
    scene_type VARCHAR(32) NOT NULL,
    device_id VARCHAR(64) NOT NULL,
    home_id VARCHAR(64) DEFAULT NULL,
    event_id VARCHAR(64) DEFAULT NULL,
    context_snapshot JSON DEFAULT NULL,
    model_name VARCHAR(64) DEFAULT NULL,
    prompt_version VARCHAR(32) DEFAULT NULL,
    diagnosis_result JSON DEFAULT NULL,
    latency_ms INT DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_diagnosis_record_diagnosis_id (diagnosis_id),
    KEY idx_ai_diagnosis_record_scene_device_created (scene_type, device_id, created_at)
);

CREATE TABLE IF NOT EXISTS ai_feedback_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    feedback_id VARCHAR(64) NOT NULL,
    diagnosis_id VARCHAR(64) NOT NULL,
    feedback_type VARCHAR(32) NOT NULL,
    resolution_status VARCHAR(32) NOT NULL,
    operator_id VARCHAR(64) DEFAULT NULL,
    resolution_note VARCHAR(1000) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_feedback_record_feedback_id (feedback_id),
    KEY idx_ai_feedback_record_diagnosis_id (diagnosis_id)
);

CREATE TABLE IF NOT EXISTS ai_case_library (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    case_id VARCHAR(64) NOT NULL,
    scene_type VARCHAR(32) NOT NULL,
    symptom VARCHAR(500) NOT NULL,
    root_cause VARCHAR(500) NOT NULL,
    resolution TEXT NOT NULL,
    effectiveness_score DECIMAL(5,2) DEFAULT NULL,
    source_feedback_id VARCHAR(64) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_case_library_case_id (case_id),
    KEY idx_ai_case_library_scene_score_created (scene_type, effectiveness_score, created_at)
);
