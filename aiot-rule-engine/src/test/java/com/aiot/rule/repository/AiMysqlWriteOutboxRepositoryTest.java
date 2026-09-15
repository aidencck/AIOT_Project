package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
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

class AiMysqlWriteOutboxRepositoryTest {

    @Test
    void shouldPersistOutboxTaskIntoMysqlStoreWhenAvailable() {
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
        AiMysqlWriteOutboxRepository repository = new AiMysqlWriteOutboxRepository(
                redisTemplate,
                provider,
                properties,
                new ObjectMapper()
        );
        AiMysqlWriteOutboxTask task = AiMysqlWriteOutboxTask.builder()
                .taskId("task-1")
                .entityType("diagnosis")
                .recordKey("diag-1")
                .payloadJson("{}")
                .lastError("boom")
                .failedAt(1000L)
                .retryCount(0)
                .build();

        repository.save(task);

        verify(jdbcTemplate).update(anyString(), eq("task-1"), eq("diagnosis"), eq("diag-1"), eq("{}"), eq("boom"), eq(1000L), eq(null), eq(0), eq(null));
        verify(hashOperations).delete(anyString(), eq("task-1"));
        verify(hashOperations, never()).put(anyString(), any(), any());
    }

    @Test
    void shouldMergeMysqlAndRedisPendingTasks() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ObjectMapper objectMapper = new ObjectMapper();
        when(hashOperations.entries(anyString())).thenReturn(java.util.Map.of(
                "task-redis", objectMapper.writeValueAsString(AiMysqlWriteOutboxTask.builder()
                        .taskId("task-redis")
                        .entityType("feedback")
                        .recordKey("fb-1")
                        .payloadJson("{}")
                        .failedAt(3000L)
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
                AiMysqlWriteOutboxTask.builder()
                        .taskId("task-mysql")
                        .entityType("diagnosis")
                        .recordKey("diag-1")
                        .payloadJson("{}")
                        .failedAt(1000L)
                        .retryCount(0)
                        .build()
        ));
        AiMysqlWriteOutboxRepository repository = new AiMysqlWriteOutboxRepository(
                redisTemplate,
                provider,
                properties,
                objectMapper
        );

        List<AiMysqlWriteOutboxTask> tasks = repository.listPending(10);

        assertEquals(2, tasks.size());
        assertEquals("task-mysql", tasks.get(0).getTaskId());
        assertEquals("task-redis", tasks.get(1).getTaskId());
    }

    @Test
    void shouldExcludeDeadLetterTasksFromPending() throws Exception {
        @SuppressWarnings("unchecked")
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        ObjectMapper objectMapper = new ObjectMapper();
        when(hashOperations.entries(anyString())).thenReturn(java.util.Map.of(
                "task-dead", objectMapper.writeValueAsString(AiMysqlWriteOutboxTask.builder()
                        .taskId("task-dead")
                        .entityType("diagnosis")
                        .recordKey("diag-1")
                        .payloadJson("{}")
                        .failedAt(1000L)
                        .retryCount(5)
                        .deadLetter(true)
                        .build()),
                "task-live", objectMapper.writeValueAsString(AiMysqlWriteOutboxTask.builder()
                        .taskId("task-live")
                        .entityType("diagnosis")
                        .recordKey("diag-2")
                        .payloadJson("{}")
                        .failedAt(2000L)
                        .retryCount(1)
                        .deadLetter(false)
                        .build())
        ));
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(false);
        AiMysqlWriteOutboxRepository repository = new AiMysqlWriteOutboxRepository(
                redisTemplate,
                provider,
                properties,
                objectMapper
        );

        List<AiMysqlWriteOutboxTask> tasks = repository.listPending(10);

        assertEquals(1, tasks.size());
        assertEquals("task-live", tasks.get(0).getTaskId());
    }
}
