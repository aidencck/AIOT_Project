package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiCaseMaterializationTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCaseMaterializationTaskRepositoryTest {

    @Test
    void shouldPersistCaseTaskIntoMysqlStoreWhenAvailable() {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        AiCaseMaterializationTaskRepository repository = new AiCaseMaterializationTaskRepository(
                redisTemplate,
                provider,
                properties,
                new ObjectMapper()
        );
        AiCaseMaterializationTask task = AiCaseMaterializationTask.builder()
                .taskId("task-1")
                .diagnosisId("diag-1")
                .feedbackId("fb-1")
                .feedbackType("ACCEPTED")
                .resolutionStatus("SOLVED")
                .resolutionNote("done")
                .operatorId("op-1")
                .queuedAt(1000L)
                .retryCount(0)
                .lastError("case_failed")
                .build();

        repository.save(task);

        verify(jdbcTemplate).update(
                anyString(),
                eq("task-1"),
                eq("diag-1"),
                eq("fb-1"),
                eq("ACCEPTED"),
                eq("SOLVED"),
                eq("done"),
                eq("op-1"),
                eq(1000L),
                eq(null),
                eq(0),
                eq("case_failed")
        );
        verify(hashOperations).delete(anyString(), eq("task-1"));
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    void shouldMergeMysqlAndRedisPendingCaseTasks() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ObjectMapper objectMapper = new ObjectMapper();
        when(hashOperations.entries(anyString())).thenReturn(java.util.Map.of(
                "task-redis", objectMapper.writeValueAsString(AiCaseMaterializationTask.builder()
                        .taskId("task-redis")
                        .diagnosisId("diag-redis")
                        .feedbackId("fb-redis")
                        .queuedAt(3000L)
                        .retryCount(1)
                        .build())
        ));
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbcTemplate);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt())).thenReturn(List.of(
                AiCaseMaterializationTask.builder()
                        .taskId("task-mysql")
                        .diagnosisId("diag-mysql")
                        .feedbackId("fb-mysql")
                        .queuedAt(1000L)
                        .retryCount(0)
                        .build()
        ));
        AiCaseMaterializationTaskRepository repository = new AiCaseMaterializationTaskRepository(
                redisTemplate,
                provider,
                properties,
                objectMapper
        );

        List<AiCaseMaterializationTask> tasks = repository.listPending(10);

        assertEquals(2, tasks.size());
        assertEquals("task-mysql", tasks.get(0).getTaskId());
        assertEquals("task-redis", tasks.get(1).getTaskId());
    }
}
