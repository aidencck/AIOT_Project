package com.aiot.rule.repository;

import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.model.AiFeedbackRecord;
import com.aiot.rule.service.AiPersistenceMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.List;

@Component
public class AiMysqlRecordReader {

    private final JdbcTemplate jdbcTemplate;
    private final AiPersistenceMetrics aiPersistenceMetrics;

    public AiMysqlRecordReader(@Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                               AiPersistenceMetrics aiPersistenceMetrics) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.aiPersistenceMetrics = aiPersistenceMetrics;
    }

    public AiDiagnosisRecord findDiagnosisById(String diagnosisId) {
        if (jdbcTemplate == null) {
            aiPersistenceMetrics.recordMysqlRead("diagnosis", "unavailable", 0L);
            return null;
        }
        long startedAt = System.nanoTime();
        try {
            List<AiDiagnosisRecord> rows = jdbcTemplate.query(
                    """
                            SELECT diagnosis_id, trace_id, scene_type, device_id, home_id, event_id, context_snapshot, model_name, prompt_version, diagnosis_result, latency_ms, created_at
                            FROM ai_diagnosis_record
                            WHERE diagnosis_id = ?
                            LIMIT 1
                            """,
                    (rs, rowNum) -> AiDiagnosisRecord.builder()
                            .diagnosisId(rs.getString("diagnosis_id"))
                            .traceId(rs.getString("trace_id"))
                            .sceneType(rs.getString("scene_type"))
                            .deviceId(rs.getString("device_id"))
                            .homeId(rs.getString("home_id"))
                            .eventId(rs.getString("event_id"))
                            .contextSnapshot(rs.getString("context_snapshot"))
                            .modelName(rs.getString("model_name"))
                            .promptVersion(rs.getString("prompt_version"))
                            .diagnosisResult(rs.getString("diagnosis_result"))
                            .latencyMs(rs.getObject("latency_ms") == null ? null : rs.getLong("latency_ms"))
                            .createdAt(toEpochMillis(rs.getTimestamp("created_at")))
                            .build(),
                    diagnosisId
            );
            AiDiagnosisRecord result = rows.isEmpty() ? null : rows.get(0);
            aiPersistenceMetrics.recordMysqlRead("diagnosis", result == null ? "miss" : "hit", System.nanoTime() - startedAt);
            return result;
        } catch (Exception ex) {
            aiPersistenceMetrics.recordMysqlRead("diagnosis", "failed", System.nanoTime() - startedAt);
            throw ex;
        }
    }

    public AiFeedbackRecord findFeedbackById(String feedbackId) {
        if (jdbcTemplate == null) {
            aiPersistenceMetrics.recordMysqlRead("feedback", "unavailable", 0L);
            return null;
        }
        long startedAt = System.nanoTime();
        try {
            List<AiFeedbackRecord> rows = jdbcTemplate.query(
                    """
                            SELECT feedback_id, diagnosis_id, feedback_type, resolution_status, operator_id, resolution_note, created_at
                            FROM ai_feedback_record
                            WHERE feedback_id = ?
                            LIMIT 1
                            """,
                    (rs, rowNum) -> AiFeedbackRecord.builder()
                            .feedbackId(rs.getString("feedback_id"))
                            .diagnosisId(rs.getString("diagnosis_id"))
                            .feedbackType(rs.getString("feedback_type"))
                            .resolutionStatus(rs.getString("resolution_status"))
                            .operatorId(rs.getString("operator_id"))
                            .resolutionNote(rs.getString("resolution_note"))
                            .createdAt(toEpochMillis(rs.getTimestamp("created_at")))
                            .build(),
                    feedbackId
            );
            AiFeedbackRecord result = rows.isEmpty() ? null : rows.get(0);
            aiPersistenceMetrics.recordMysqlRead("feedback", result == null ? "miss" : "hit", System.nanoTime() - startedAt);
            return result;
        } catch (Exception ex) {
            aiPersistenceMetrics.recordMysqlRead("feedback", "failed", System.nanoTime() - startedAt);
            throw ex;
        }
    }

    public List<AiCaseRecord> findCasesBySceneType(String sceneType, int limit) {
        if (jdbcTemplate == null) {
            aiPersistenceMetrics.recordMysqlRead("cases", "unavailable", 0L);
            return List.of();
        }
        long startedAt = System.nanoTime();
        try {
            List<AiCaseRecord> rows = jdbcTemplate.query(
                    """
                            SELECT case_id, scene_type, symptom, root_cause, resolution, effectiveness_score, source_feedback_id, created_at
                            FROM ai_case_library
                            WHERE (? IS NULL OR ? = '' OR scene_type = ?)
                            ORDER BY effectiveness_score DESC, created_at DESC
                            LIMIT ?
                            """,
                    (rs, rowNum) -> AiCaseRecord.builder()
                            .caseId(rs.getString("case_id"))
                            .sceneType(rs.getString("scene_type"))
                            .symptom(rs.getString("symptom"))
                            .rootCause(rs.getString("root_cause"))
                            .resolution(rs.getString("resolution"))
                            .effectivenessScore(rs.getObject("effectiveness_score") == null
                                    ? null
                                    : rs.getDouble("effectiveness_score"))
                            .sourceFeedbackId(rs.getString("source_feedback_id"))
                            .createdAt(toEpochMillis(rs.getTimestamp("created_at")))
                            .build(),
                    sceneType,
                    sceneType,
                    sceneType,
                    Math.max(limit, 1)
            );
            aiPersistenceMetrics.recordMysqlRead("cases", rows.isEmpty() ? "miss" : "hit", System.nanoTime() - startedAt);
            return rows;
        } catch (Exception ex) {
            aiPersistenceMetrics.recordMysqlRead("cases", "failed", System.nanoTime() - startedAt);
            throw ex;
        }
    }

    private Long toEpochMillis(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.getTime();
    }
}
