package com.aiot.rule.repository;

import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.model.AiFeedbackRecord;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

@Slf4j
@Component
public class AiMysqlRecordWriter {

    private static final int MAX_RETRY_COUNT = 5;

    private static final String UPSERT_DIAGNOSIS_SQL = """
            INSERT INTO ai_diagnosis_record
            (diagnosis_id, trace_id, scene_type, device_id, home_id, event_id, context_snapshot, model_name, prompt_version, diagnosis_result, latency_ms, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            trace_id = VALUES(trace_id),
            scene_type = VALUES(scene_type),
            device_id = VALUES(device_id),
            home_id = VALUES(home_id),
            event_id = VALUES(event_id),
            context_snapshot = VALUES(context_snapshot),
            model_name = VALUES(model_name),
            prompt_version = VALUES(prompt_version),
            diagnosis_result = VALUES(diagnosis_result),
            latency_ms = VALUES(latency_ms),
            created_at = VALUES(created_at)
            """;

    private static final String UPSERT_FEEDBACK_SQL = """
            INSERT INTO ai_feedback_record
            (feedback_id, diagnosis_id, feedback_type, resolution_status, operator_id, resolution_note, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            diagnosis_id = VALUES(diagnosis_id),
            feedback_type = VALUES(feedback_type),
            resolution_status = VALUES(resolution_status),
            operator_id = VALUES(operator_id),
            resolution_note = VALUES(resolution_note),
            created_at = VALUES(created_at)
            """;

    private static final String UPSERT_CASE_SQL = """
            INSERT INTO ai_case_library
            (case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            scene_type = VALUES(scene_type),
            symptom = VALUES(symptom),
            root_cause = VALUES(root_cause),
            resolution = VALUES(resolution),
            effectiveness_score = VALUES(effectiveness_score),
            source_feedback_id = VALUES(source_feedback_id),
            created_at = VALUES(created_at)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final AiPersistenceMetrics aiPersistenceMetrics;
    private final AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository;
    private final ObjectMapper objectMapper;

    public AiMysqlRecordWriter(@Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                               AiPersistenceMetrics aiPersistenceMetrics,
                               AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository,
                               ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.aiPersistenceMetrics = aiPersistenceMetrics;
        this.aiMysqlWriteOutboxRepository = aiMysqlWriteOutboxRepository;
        this.objectMapper = objectMapper;
    }

    public boolean saveDiagnosis(AiDiagnosisRecord record) {
        if (jdbcTemplate == null || record == null) {
            aiPersistenceMetrics.recordMysqlWrite("diagnosis", "skipped", 0L);
            return false;
        }
        return execute("diagnosis", "diagnosisId=" + record.getDiagnosisId(), () -> jdbcTemplate.update(
                UPSERT_DIAGNOSIS_SQL,
                record.getDiagnosisId(),
                record.getTraceId(),
                record.getSceneType(),
                record.getDeviceId(),
                record.getHomeId(),
                record.getEventId(),
                record.getContextSnapshot(),
                record.getModelName(),
                record.getPromptVersion(),
                record.getDiagnosisResult(),
                record.getLatencyMs(),
                toTimestamp(record.getCreatedAt())
        ), null, toOutboxTask("diagnosis", record.getDiagnosisId(), record));
    }

    public boolean saveFeedback(AiFeedbackRecord record) {
        if (jdbcTemplate == null || record == null) {
            aiPersistenceMetrics.recordMysqlWrite("feedback", "skipped", 0L);
            return false;
        }
        return execute("feedback", "feedbackId=" + record.getFeedbackId(), () -> jdbcTemplate.update(
                UPSERT_FEEDBACK_SQL,
                record.getFeedbackId(),
                record.getDiagnosisId(),
                record.getFeedbackType(),
                record.getResolutionStatus(),
                record.getOperatorId(),
                record.getResolutionNote(),
                toTimestamp(record.getCreatedAt())
        ), null, toOutboxTask("feedback", record.getFeedbackId(), record));
    }

    public boolean saveCase(AiCaseRecord record) {
        if (jdbcTemplate == null || record == null) {
            aiPersistenceMetrics.recordMysqlWrite("cases", "skipped", 0L);
            return false;
        }
        return execute("cases", "caseId=" + record.getCaseId(), () -> jdbcTemplate.update(
                UPSERT_CASE_SQL,
                record.getCaseId(),
                record.getSceneType(),
                record.getSymptom(),
                record.getRootCause(),
                record.getResolution(),
                record.getEffectivenessScore(),
                record.getSourceFeedbackId(),
                toTimestamp(record.getCreatedAt())
        ), null, toOutboxTask("cases", record.getCaseId(), record));
    }

    public boolean replay(AiMysqlWriteOutboxTask task) {
        if (task == null || !hasText(task.getPayloadJson()) || !hasText(task.getEntityType())) {
            return false;
        }
        return switch (task.getEntityType()) {
            case "diagnosis" -> replayDiagnosis(task);
            case "feedback" -> replayFeedback(task);
            case "cases" -> replayCase(task);
            default -> {
                updateOutboxFailure(task, "Unsupported entityType=" + task.getEntityType());
                yield false;
            }
        };
    }

    private boolean replayDiagnosis(AiMysqlWriteOutboxTask task) {
        try {
            AiDiagnosisRecord record = objectMapper.readValue(task.getPayloadJson(), AiDiagnosisRecord.class);
            return execute("diagnosis", "diagnosisId=" + task.getRecordKey(), () -> jdbcTemplate.update(
                    UPSERT_DIAGNOSIS_SQL,
                    record.getDiagnosisId(),
                    record.getTraceId(),
                    record.getSceneType(),
                    record.getDeviceId(),
                    record.getHomeId(),
                    record.getEventId(),
                    record.getContextSnapshot(),
                    record.getModelName(),
                    record.getPromptVersion(),
                    record.getDiagnosisResult(),
                    record.getLatencyMs(),
                    toTimestamp(record.getCreatedAt())
            ), task, null);
        } catch (Exception ex) {
            updateOutboxFailure(task, ex.getMessage());
            return false;
        }
    }

    private boolean replayFeedback(AiMysqlWriteOutboxTask task) {
        try {
            AiFeedbackRecord record = objectMapper.readValue(task.getPayloadJson(), AiFeedbackRecord.class);
            return execute("feedback", "feedbackId=" + task.getRecordKey(), () -> jdbcTemplate.update(
                    UPSERT_FEEDBACK_SQL,
                    record.getFeedbackId(),
                    record.getDiagnosisId(),
                    record.getFeedbackType(),
                    record.getResolutionStatus(),
                    record.getOperatorId(),
                    record.getResolutionNote(),
                    toTimestamp(record.getCreatedAt())
            ), task, null);
        } catch (Exception ex) {
            updateOutboxFailure(task, ex.getMessage());
            return false;
        }
    }

    private boolean replayCase(AiMysqlWriteOutboxTask task) {
        try {
            AiCaseRecord record = objectMapper.readValue(task.getPayloadJson(), AiCaseRecord.class);
            return execute("cases", "caseId=" + task.getRecordKey(), () -> jdbcTemplate.update(
                    UPSERT_CASE_SQL,
                    record.getCaseId(),
                    record.getSceneType(),
                    record.getSymptom(),
                    record.getRootCause(),
                    record.getResolution(),
                    record.getEffectivenessScore(),
                    record.getSourceFeedbackId(),
                    toTimestamp(record.getCreatedAt())
            ), task, null);
        } catch (Exception ex) {
            updateOutboxFailure(task, ex.getMessage());
            return false;
        }
    }

    private boolean execute(String table, String key, Runnable action, AiMysqlWriteOutboxTask replayTask, AiMysqlWriteOutboxTask newTask) {
        long startedAt = System.nanoTime();
        try {
            action.run();
            aiPersistenceMetrics.recordMysqlWrite(table, "success", System.nanoTime() - startedAt);
            if (replayTask != null) {
                aiMysqlWriteOutboxRepository.delete(replayTask.getTaskId());
                aiPersistenceMetrics.recordMysqlOutbox(table, "replayed");
            }
            return true;
        } catch (Exception ex) {
            aiPersistenceMetrics.recordMysqlWrite(table, "failed", System.nanoTime() - startedAt);
            if (replayTask != null) {
                updateOutboxFailure(replayTask, ex.getMessage());
            } else if (newTask != null) {
                aiMysqlWriteOutboxRepository.save(newTask);
                aiPersistenceMetrics.recordMysqlOutbox(table, "queued");
            }
            log.warn("MySQL dual-write failed, {}", key, ex);
            return false;
        }
    }

    private void updateOutboxFailure(AiMysqlWriteOutboxTask task, String errorMessage) {
        int nextRetryCount = (task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1;
        AiMysqlWriteOutboxTask.AiMysqlWriteOutboxTaskBuilder builder = task.toBuilder()
                .lastError(trimError(errorMessage))
                .failedAt(System.currentTimeMillis())
                .lastRetryAt(System.currentTimeMillis())
                .retryCount(nextRetryCount);
        if (nextRetryCount >= MAX_RETRY_COUNT) {
            builder.deadLetter(true);
            log.error("AI MySQL outbox task marked as dead letter, taskId={}, entityType={}, recordKey={}, retryCount={}, error={}",
                    task.getTaskId(), task.getEntityType(), task.getRecordKey(), nextRetryCount, trimError(errorMessage));
        }
        aiMysqlWriteOutboxRepository.save(builder.build());
        aiPersistenceMetrics.recordMysqlOutbox(task.getEntityType(), "retry_failed");
    }

    private AiMysqlWriteOutboxTask toOutboxTask(String entityType, String recordKey, Object record) {
        return AiMysqlWriteOutboxTask.builder()
                .taskId(UUID.randomUUID().toString())
                .entityType(entityType)
                .recordKey(recordKey)
                .payloadJson(toPayloadJson(record))
                .lastError("mysql_dual_write_failed")
                .failedAt(System.currentTimeMillis())
                .lastRetryAt(null)
                .retryCount(0)
                .build();
    }

    private String toPayloadJson(Object record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize AI MySQL outbox payload", ex);
        }
    }

    private String trimError(String errorMessage) {
        if (!hasText(errorMessage)) {
            return "mysql_dual_write_failed";
        }
        return errorMessage.length() > 256 ? errorMessage.substring(0, 256) : errorMessage;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Timestamp toTimestamp(Long createdAt) {
        return new Timestamp(createdAt == null ? System.currentTimeMillis() : createdAt);
    }
}
