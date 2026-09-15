package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.service.AiPersistenceReadDecision;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.aiot.rule.service.AiPersistenceReadModeResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRepositoryReadModeTest {

    @Test
    void shouldFallbackToMysqlWhenDualModeMissesRedisDiagnosis() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get(anyString(), anyString())).thenReturn(null);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new SimpleMeterRegistry());
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        properties.setReadMode("DUAL");
        AiDiagnosisRecord mysqlRecord = AiDiagnosisRecord.builder().diagnosisId("diag-1").build();
        when(reader.findDiagnosisById("diag-1")).thenReturn(mysqlRecord);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                metrics
        );

        AiDiagnosisRecord result = repository.findById("diag-1");

        assertSame(mysqlRecord, result);
        verify(reader).findDiagnosisById("diag-1");
        verify(hashOperations, never()).get(anyString(), anyString());
    }

    @Test
    void shouldReadCasesFromMysqlWhenModeIsMysql() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new SimpleMeterRegistry());
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        properties.setReadMode("MYSQL");
        List<AiCaseRecord> mysqlCases = List.of(AiCaseRecord.builder().caseId("case-1").build());
        when(reader.findCasesBySceneType("OFFLINE_FLAP", 3)).thenReturn(mysqlCases);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));

        AiCaseRecordRepository repository = new AiCaseRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                metrics
        );

        List<AiCaseRecord> result = repository.findBySceneType("OFFLINE_FLAP", 3);

        assertEquals(1, result.size());
        assertEquals("case-1", result.get(0).getCaseId());
        verify(redisTemplate, never()).opsForHash();
    }

    @Test
    void shouldFallbackToRedisWhenMysqlModeIsGuardedOff() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        AiDiagnosisRecord redisRecord = AiDiagnosisRecord.builder().diagnosisId("diag-redis").build();
        when(hashOperations.get(anyString(), anyString())).thenReturn(new ObjectMapper().writeValueAsString(redisRecord));
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new SimpleMeterRegistry());
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        properties.setReadMode("MYSQL");
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                com.aiot.rule.config.AiPersistenceReadMode.REDIS,
                false,
                "migration gate not passed"
        ));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                metrics
        );

        AiDiagnosisRecord result = repository.findById("diag-redis");

        assertEquals("diag-redis", result.getDiagnosisId());
        verify(reader, never()).findDiagnosisById(anyString());
    }

    @Test
    void shouldFallbackToRedisWhenDualModeMysqlPrimaryMisses() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        AiDiagnosisRecord redisRecord = AiDiagnosisRecord.builder().diagnosisId("diag-redis-fallback").build();
        when(hashOperations.get(anyString(), anyString())).thenReturn(new ObjectMapper().writeValueAsString(redisRecord));
        AiMysqlRecordWriter writer = mock(AiMysqlRecordWriter.class);
        AiMysqlRecordReader reader = mock(AiMysqlRecordReader.class);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        AiPersistenceMetrics metrics = new AiPersistenceMetrics(new SimpleMeterRegistry());
        AiPersistenceReadModeResolver resolver = mock(AiPersistenceReadModeResolver.class);
        properties.setReadMode("DUAL");
        when(reader.findDiagnosisById("diag-redis-fallback")).thenReturn(null);
        when(resolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));

        AiDiagnosisRecordRepository repository = new AiDiagnosisRecordRepository(
                redisTemplate,
                new ObjectMapper(),
                writer,
                reader,
                properties,
                resolver,
                metrics
        );

        AiDiagnosisRecord result = repository.findById("diag-redis-fallback");

        assertEquals("diag-redis-fallback", result.getDiagnosisId());
        verify(reader).findDiagnosisById("diag-redis-fallback");
        verify(hashOperations).get(anyString(), anyString());
    }
}
