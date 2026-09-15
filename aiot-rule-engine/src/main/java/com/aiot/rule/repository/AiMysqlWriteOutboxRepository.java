package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Repository
public class AiMysqlWriteOutboxRepository {

    private static final String STORE_KEY = "aiot:ai:mysql-write-outbox";
    private static final String UPSERT_SQL = """
            INSERT INTO ai_mysql_write_outbox
            (task_id, entity_type, record_key, payload_json, last_error, failed_at, last_retry_at, retry_count, dead_letter)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            entity_type = VALUES(entity_type),
            record_key = VALUES(record_key),
            payload_json = VALUES(payload_json),
            last_error = VALUES(last_error),
            failed_at = VALUES(failed_at),
            last_retry_at = VALUES(last_retry_at),
            retry_count = VALUES(retry_count),
            dead_letter = VALUES(dead_letter)
            """;
    private static final String DELETE_SQL = "DELETE FROM ai_mysql_write_outbox WHERE task_id = ?";
    private static final String OLDEST_SQL = "SELECT MIN(failed_at) FROM ai_mysql_write_outbox";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final ObjectMapper objectMapper;
    private final RowMapper<AiMysqlWriteOutboxTask> rowMapper = (rs, rowNum) -> AiMysqlWriteOutboxTask.builder()
            .taskId(rs.getString("task_id"))
            .entityType(rs.getString("entity_type"))
            .recordKey(rs.getString("record_key"))
            .payloadJson(rs.getString("payload_json"))
            .lastError(rs.getString("last_error"))
            .failedAt(readLong(rs.getObject("failed_at")))
            .lastRetryAt(readLong(rs.getObject("last_retry_at")))
            .retryCount(readInteger(rs.getObject("retry_count")))
            .deadLetter(readBoolean(rs.getObject("dead_letter")))
            .build();

    public AiMysqlWriteOutboxRepository(RedisTemplate<String, Object> redisTemplate,
                                        @Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                        AiPersistenceMysqlProperties aiPersistenceMysqlProperties,
                                        ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.aiPersistenceMysqlProperties = aiPersistenceMysqlProperties;
        this.objectMapper = objectMapper;
    }

    public void save(AiMysqlWriteOutboxTask task) {
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            return;
        }
        if (useMysqlStore()) {
            try {
                jdbcTemplate.update(
                        UPSERT_SQL,
                        task.getTaskId(),
                        task.getEntityType(),
                        task.getRecordKey(),
                        task.getPayloadJson(),
                        task.getLastError(),
                        task.getFailedAt(),
                        task.getLastRetryAt(),
                        task.getRetryCount(),
                        task.getDeadLetter()
                );
                redisTemplate.opsForHash().delete(STORE_KEY, task.getTaskId());
                return;
            } catch (Exception ex) {
                log.warn("Failed to save AI MySQL outbox task into MySQL store, fallback to redis, taskId={}", task.getTaskId(), ex);
            }
        }
        redisTemplate.opsForHash().put(STORE_KEY, task.getTaskId(), toJson(task));
    }

    public void delete(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }
        if (useMysqlStore()) {
            try {
                jdbcTemplate.update(DELETE_SQL, taskId);
            } catch (Exception ex) {
                log.warn("Failed to delete AI MySQL outbox task from MySQL store, taskId={}", taskId, ex);
            }
        }
        redisTemplate.opsForHash().delete(STORE_KEY, taskId);
    }

    public long countPending() {
        return mergeTasks(loadMysqlPending(Integer.MAX_VALUE), loadRedisPending(Integer.MAX_VALUE)).size();
    }

    public Long findOldestFailedAt() {
        Long mysqlOldest = null;
        if (useMysqlStore()) {
            try {
                mysqlOldest = jdbcTemplate.queryForObject(OLDEST_SQL, Long.class);
            } catch (Exception ex) {
                log.warn("Failed to query oldest AI MySQL outbox task from MySQL store", ex);
            }
        }
        Long mergedOldest = listPending(Integer.MAX_VALUE).stream()
                .map(AiMysqlWriteOutboxTask::getFailedAt)
                .filter(Objects::nonNull)
                .min(Long::compareTo)
                .orElse(null);
        if (mysqlOldest == null) {
            return mergedOldest;
        }
        if (mergedOldest == null) {
            return mysqlOldest;
        }
        return Math.min(mysqlOldest, mergedOldest);
    }

    public List<AiMysqlWriteOutboxTask> listPending(int limit) {
        return mergeTasks(loadMysqlPending(limit), loadRedisPending(limit)).stream()
                .limit(Math.max(limit, 1))
                .toList();
    }

    public String storageBackendMode() {
        return useMysqlStore() ? "MYSQL_TABLE_PRIMARY" : "REDIS_HASH_ONLY";
    }

    public long countLegacyRedisPending() {
        return loadRedisPending(Integer.MAX_VALUE).size();
    }

    public List<AiMysqlWriteOutboxTask> listLegacyRedisPending(int limit) {
        return loadRedisPending(limit);
    }

    private boolean useMysqlStore() {
        return aiPersistenceMysqlProperties.isEnabled() && jdbcTemplate != null;
    }

    private List<AiMysqlWriteOutboxTask> loadMysqlPending(int limit) {
        if (!useMysqlStore()) {
            return List.of();
        }
        try {
            List<AiMysqlWriteOutboxTask> tasks = jdbcTemplate.query(
                    """
                            SELECT task_id, entity_type, record_key, payload_json, last_error, failed_at, last_retry_at, retry_count, dead_letter
                            FROM ai_mysql_write_outbox
                            ORDER BY failed_at ASC, retry_count ASC
                            LIMIT ?
                            """,
                    rowMapper,
                    Math.max(limit, 1)
            );
            return tasks.stream()
                    .filter(task -> !isDeadLetter(task))
                    .toList();
        } catch (Exception ex) {
            log.warn("Failed to load AI MySQL outbox tasks from MySQL store", ex);
            return List.of();
        }
    }

    private List<AiMysqlWriteOutboxTask> loadRedisPending(int limit) {
        HashOperations<String, Object, Object> hashOperations = redisTemplate.opsForHash();
        Map<Object, Object> entries = hashOperations.entries(STORE_KEY);
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(Objects::nonNull)
                .filter(task -> !isDeadLetter(task))
                .sorted(Comparator.comparing(AiMysqlWriteOutboxTask::getFailedAt, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(AiMysqlWriteOutboxTask::getRetryCount, Comparator.nullsLast(Integer::compareTo)))
                .limit(Math.max(limit, 1))
                .toList();
    }

    private boolean isDeadLetter(AiMysqlWriteOutboxTask task) {
        return task != null && Boolean.TRUE.equals(task.getDeadLetter());
    }

    private List<AiMysqlWriteOutboxTask> mergeTasks(List<AiMysqlWriteOutboxTask> mysqlTasks, List<AiMysqlWriteOutboxTask> redisTasks) {
        Map<String, AiMysqlWriteOutboxTask> merged = new LinkedHashMap<>();
        for (AiMysqlWriteOutboxTask task : mysqlTasks) {
            if (task != null && StringUtils.hasText(task.getTaskId())) {
                merged.put(task.getTaskId(), task);
            }
        }
        for (AiMysqlWriteOutboxTask task : redisTasks) {
            if (task != null && StringUtils.hasText(task.getTaskId())) {
                merged.putIfAbsent(task.getTaskId(), task);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparing(AiMysqlWriteOutboxTask::getFailedAt, Comparator.nullsLast(Long::compareTo))
                        .thenComparing(AiMysqlWriteOutboxTask::getRetryCount, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private Long readLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private Boolean readBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String toJson(AiMysqlWriteOutboxTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AI MySQL outbox task", ex);
        }
    }

    private AiMysqlWriteOutboxTask fromJson(Object payload, String taskId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiMysqlWriteOutboxTask.class);
        } catch (Exception ex) {
            log.warn("Skip invalid AI MySQL outbox task, taskId={}", taskId, ex);
            return null;
        }
    }
}
