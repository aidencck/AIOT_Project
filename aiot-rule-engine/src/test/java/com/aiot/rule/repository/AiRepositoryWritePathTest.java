package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.model.AiFeedbackRecord;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.aiot.rule.service.AiPersistenceReadDecision;
import com.aiot.rule.service.AiPersistenceReadModeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static com.aiot.rule.config.AiPersistenceReadMode.DUAL;
import static com.aiot.rule.config.AiPersistenceReadMode.REDIS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRepositoryWritePathTest {

    @Test
    void shouldWriteDiagnosisToMysqlBeforeRedisMirror() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        when(writer.saveDiagnosis(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(REDIS, REDIS, true, null));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                new AiPersistenceMetrics(new SimpleMeterRegistry())
        );
        AiDiagnosisRecord record = AiDiagnosisRecord.builder().diagnosisId("diag-1").build();

        repository.save(record);

        InOrder inOrder = inOrder(writer, hashOperations);
        inOrder.verify(writer).saveDiagnosis(record);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(hashOperations).put(eq("aiot:ai:diagnosis-records"), eq("diag-1"), payloadCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"diagnosisId\":\"diag-1\""));
    }

    @Test
    void shouldWriteFeedbackToMysqlBeforeRedisMirror() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        when(writer.saveFeedback(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(REDIS, REDIS, true, null));

        AiFeedbackRecordRepository repository = new AiFeedbackRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                new AiPersistenceMetrics(new SimpleMeterRegistry())
        );
        AiFeedbackRecord record = AiFeedbackRecord.builder().feedbackId("fb-1").build();

        repository.save(record);

        InOrder inOrder = inOrder(writer, hashOperations);
        inOrder.verify(writer).saveFeedback(record);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(hashOperations).put(eq("aiot:ai:feedback-records"), eq("fb-1"), payloadCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"feedbackId\":\"fb-1\""));
    }

    @Test
    void shouldWriteCaseToMysqlBeforeRedisMirror() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        when(writer.saveCase(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(REDIS, REDIS, true, null));

        AiCaseRecordRepository repository = new AiCaseRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                new AiPersistenceMetrics(new SimpleMeterRegistry())
        );
        AiCaseRecord record = AiCaseRecord.builder().caseId("case-1").build();

        repository.save(record);

        InOrder inOrder = inOrder(writer, hashOperations);
        inOrder.verify(writer).saveCase(record);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        inOrder.verify(hashOperations).put(eq("aiot:ai:case-records"), eq("case-1"), payloadCaptor.capture());
        org.junit.jupiter.api.Assertions.assertTrue(String.valueOf(payloadCaptor.getValue()).contains("\"caseId\":\"case-1\""));
    }

    @Test
    void shouldFailDiagnosisSaveWhenRedisMirrorFailsAndEffectiveModeIsRedis() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        doThrow(new RuntimeException("redis down"))
                .when(hashOperations)
                .put(anyString(), any(), any());
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        when(writer.saveDiagnosis(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(REDIS, REDIS, true, null));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                new AiPersistenceMetrics(new SimpleMeterRegistry())
        );

        assertThrows(IllegalStateException.class,
                () -> repository.save(AiDiagnosisRecord.builder().diagnosisId("diag-2").build()));
    }

    @Test
    void shouldTolerateDiagnosisRedisMirrorFailureWhenMysqlPrimaryIsAvailable() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        doThrow(new RuntimeException("redis down"))
                .when(hashOperations)
                .put(anyString(), any(), any());
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        when(writer.saveDiagnosis(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setReadMode("DUAL");
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(DUAL, DUAL, true, null));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                new AiPersistenceMetrics(new SimpleMeterRegistry())
        );

        assertDoesNotThrow(() -> repository.save(AiDiagnosisRecord.builder().diagnosisId("diag-3").build()));
    }
}
