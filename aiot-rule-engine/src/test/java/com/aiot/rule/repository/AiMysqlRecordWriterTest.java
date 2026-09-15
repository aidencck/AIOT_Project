package com.aiot.rule.repository;

import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiot.rule.service.AiPersistenceMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiMysqlRecordWriterTest {

    @Test
    void shouldSkipDualWriteWhenJdbcTemplateIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiMysqlRecordWriter writer = new AiMysqlRecordWriter(
                provider,
                new AiPersistenceMetrics(new SimpleMeterRegistry()),
                outboxRepository,
                new ObjectMapper()
        );

        writer.saveDiagnosis(AiDiagnosisRecord.builder().diagnosisId("diag-1").build());

        verify(provider).getIfAvailable();
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void shouldWriteDiagnosisToMysqlWhenJdbcTemplateAvailable() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiMysqlRecordWriter writer = new AiMysqlRecordWriter(
                provider,
                new AiPersistenceMetrics(new SimpleMeterRegistry()),
                outboxRepository,
                new ObjectMapper()
        );
        AiDiagnosisRecord record = AiDiagnosisRecord.builder()
                .diagnosisId("diag-1")
                .traceId("trace-1")
                .sceneType("OFFLINE_FLAP")
                .deviceId("dev-1")
                .homeId("home-1")
                .eventId("evt-1")
                .contextSnapshot("{}")
                .modelName("model-a")
                .promptVersion("v1")
                .diagnosisResult("{\"summary\":\"ok\"}")
                .latencyMs(123L)
                .createdAt(1000L)
                .build();
        String expectedSql = """
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

        writer.saveDiagnosis(record);

        verify(jdbcTemplate).update(
                eq(expectedSql),
                eq("diag-1"),
                eq("trace-1"),
                eq("OFFLINE_FLAP"),
                eq("dev-1"),
                eq("home-1"),
                eq("evt-1"),
                eq("{}"),
                eq("model-a"),
                eq("v1"),
                eq("{\"summary\":\"ok\"}"),
                eq(123L),
                any(Timestamp.class)
        );
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void shouldQueueOutboxTaskWhenMysqlWriteFails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(jdbcTemplate.update(any(String.class), any(Object[].class)))
                .thenThrow(new RuntimeException("mysql down"));
        AiMysqlRecordWriter writer = new AiMysqlRecordWriter(
                provider,
                new AiPersistenceMetrics(new SimpleMeterRegistry()),
                outboxRepository,
                new ObjectMapper()
        );

        writer.saveDiagnosis(AiDiagnosisRecord.builder()
                .diagnosisId("diag-2")
                .traceId("trace-2")
                .sceneType("OFFLINE_FLAP")
                .deviceId("dev-2")
                .homeId("home-2")
                .eventId("evt-2")
                .contextSnapshot("{}")
                .modelName("model-b")
                .promptVersion("v1")
                .diagnosisResult("{\"summary\":\"failed\"}")
                .latencyMs(20L)
                .createdAt(2000L)
                .build());

        verify(outboxRepository).save(any());
    }

    @Test
    void shouldMarkDeadLetterWhenRetryCountReachesMax() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiMysqlRecordWriter writer = new AiMysqlRecordWriter(
                provider,
                new AiPersistenceMetrics(new SimpleMeterRegistry()),
                outboxRepository,
                new ObjectMapper()
        );
        AiMysqlWriteOutboxTask task = AiMysqlWriteOutboxTask.builder()
                .taskId("task-1")
                .entityType("unsupported")
                .recordKey("record-1")
                .payloadJson("{}")
                .failedAt(1000L)
                .retryCount(4)
                .build();

        writer.replay(task);

        ArgumentCaptor<AiMysqlWriteOutboxTask> captor = ArgumentCaptor.forClass(AiMysqlWriteOutboxTask.class);
        verify(outboxRepository).save(captor.capture());
        AiMysqlWriteOutboxTask saved = captor.getValue();
        assertEquals(5, saved.getRetryCount());
        assertEquals(Boolean.TRUE, saved.getDeadLetter());
        assertNotNull(saved.getFailedAt());
        assertNotNull(saved.getLastRetryAt());
    }
}
