package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AiCaseMaterializationTask;
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
public class AiCaseMaterializationTaskRepository {

    private static final String STORE_KEY = "aiot:ai:case-materialization-outbox";
    private static final String UPSERT_SQL = """
            INSERT INTO ai_case_materialization_task
            (task_id, diagnosis_id, feedback_id, feedback_type, resolution_status, resolution_note, operator_id, queued_at, last_retry_at, retry_count, last_error)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
            diagnosis_id = VALUES(diagnosis_id),
            feedback_id = VALUES(feedback_id),
            feedback_type = VALUES(feedback_type),
            resolution_status = VALUES(resolution_status),
            resolution_note = VALUES(resolution_note),
            operator_id = VALUES(operator_id),
            queued_at = VALUES(queued_at),
            last_retry_at = VALUES(last_retry_at),
            retry_count = VALUES(retry_count),
            last_error = VALUES(last_error)
            """;
    private static final String DELETE_SQL = "DELETE FROM ai_case_materialization_task WHERE task_id = ?";
    private static final String OLDEST_SQL = "SELECT MIN(queued_at) FROM ai_case_materialization_task";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final ObjectMapper objectMapper;
    private final RowMapper<AiCaseMaterializationTask> rowMapper = (rs, rowNum) -> AiCaseMaterializationTask.builder()
            .taskId(rs.getString("task_id"))
            .diagnosisId(rs.getString("diagnosis_id"))
            .feedbackId(rs.getString("feedback_id"))
            .feedbackType(rs.getString("feedback_type"))
            .resolutionStatus(rs.getString("resolution_status"))
            .resolutionNote(rs.getString("resolution_note"))
            .operatorId(rs.getString("operator_id"))
            .queuedAt(readLong(rs.getObject("queued_at")))
            .lastRetryAt(readLong(rs.getObject("last_retry_at")))
            .retryCount(readInteger(rs.getObject("retry_count")))
            .lastError(rs.getString("last_error"))
            .build();

    public AiCaseMaterializationTaskRepository(RedisTemplate<String, Object> redisTemplate,
                                               @Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                               AiPersistenceMysqlProperties aiPersistenceMysqlProperties,
                                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.aiPersistenceMysqlProperties = aiPersistenceMysqlProperties;
        this.objectMapper = objectMapper;
    }

    public void save(AiCaseMaterializationTask task) {
        if (task == null || !StringUtils.hasText(task.getTaskId())) {
            return;
        }
        if (useMysqlStore()) {
            try {
                jdbcTemplate.update(
                        UPSERT_SQL,
                        task.getTaskId(),
                        task.getDiagnosisId(),
                        task.getFeedbackId(),
                        task.getFeedbackType(),
                        task.getResolutionStatus(),
                        task.getResolutionNote(),
                        task.getOperatorId(),
                        task.getQueuedAt(),
                        task.getLastRetryAt(),
                        task.getRetryCount(),
                        task.getLastError()
                );
                redisTemplate.opsForHash().delete(STORE_KEY, (Object) task.getTaskId());
                return;
            } catch (Exception ex) {
                log.warn("Failed to save AI case materialization task into MySQL store, fallback to redis, taskId={}", task.getTaskId(), ex);
            }
        }
        redisTemplate.opsForHash().put(STORE_KEY, (Object) task.getTaskId(), (Object) toJson(task));
    }

    public void delete(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return;
        }
        if (useMysqlStore()) {
            try {
                jdbcTemplate.update(DELETE_SQL, taskId);
            } catch (Exception ex) {
                log.warn("Failed to delete AI case materialization task from MySQL store, taskId={}", taskId, ex);
            }
        }
        redisTemplate.opsForHash().delete(STORE_KEY, (Object) taskId);
    }

    public long countPending() {
        return mergeTasks(loadMysqlPending(Integer.MAX_VALUE), loadRedisPending(Integer.MAX_VALUE)).size();
    }

    public Long findOldestQueuedAt() {
        Long mysqlOldest = null;
        if (useMysqlStore()) {
            try {
                mysqlOldest = jdbcTemplate.queryForObject(OLDEST_SQL, Long.class);
            } catch (Exception ex) {
                log.warn("Failed to query oldest AI case materialization task from MySQL store", ex);
            }
        }
        Long mergedOldest = null;
        for (AiCaseMaterializationTask task : listPending(Integer.MAX_VALUE)) {
            if (task == null || task.getQueuedAt() == null) {
                continue;
            }
            if (mergedOldest == null || task.getQueuedAt() < mergedOldest) {
                mergedOldest = task.getQueuedAt();
            }
        }
        if (mysqlOldest == null) {
            return mergedOldest;
        }
        if (mergedOldest == null) {
            return mysqlOldest;
        }
        return Math.min(mysqlOldest, mergedOldest);
    }

    public List<AiCaseMaterializationTask> listPending(int limit) {
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

    public List<AiCaseMaterializationTask> listLegacyRedisPending(int limit) {
        return loadRedisPending(limit);
    }

    private boolean useMysqlStore() {
        return aiPersistenceMysqlProperties.isEnabled() && jdbcTemplate != null;
    }

    private List<AiCaseMaterializationTask> loadMysqlPending(int limit) {
        if (!useMysqlStore()) {
            return List.of();
        }
        try {
            return jdbcTemplate.query(
                    """
                            SELECT task_id, diagnosis_id, feedback_id, feedback_type, resolution_status, resolution_note, operator_id,
                                   queued_at, last_retry_at, retry_count, last_error
                            FROM ai_case_materialization_task
                            ORDER BY queued_at ASC, retry_count ASC
                            LIMIT ?
                            """,
                    rowMapper,
                    Math.max(limit, 1)
            );
        } catch (Exception ex) {
            log.warn("Failed to load AI case materialization tasks from MySQL store", ex);
            return List.of();
        }
    }

    private List<AiCaseMaterializationTask> loadRedisPending(int limit) {
        HashOperations<String, Object, Object> operations = redisTemplate.opsForHash();
        Map<Object, Object> entries = operations.entries(STORE_KEY);
        Comparator<AiCaseMaterializationTask> comparator = Comparator
                .comparing((AiCaseMaterializationTask task) -> task.getQueuedAt(), Comparator.nullsLast(Long::compareTo))
                .thenComparing(task -> task.getRetryCount(), Comparator.nullsLast(Integer::compareTo));
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(Objects::nonNull)
                .sorted(comparator)
                .limit(Math.max(limit, 1))
                .toList();
    }

    private List<AiCaseMaterializationTask> mergeTasks(List<AiCaseMaterializationTask> mysqlTasks, List<AiCaseMaterializationTask> redisTasks) {
        Map<String, AiCaseMaterializationTask> merged = new LinkedHashMap<>();
        for (AiCaseMaterializationTask task : mysqlTasks) {
            if (task != null && StringUtils.hasText(task.getTaskId())) {
                merged.put(task.getTaskId(), task);
            }
        }
        for (AiCaseMaterializationTask task : redisTasks) {
            if (task != null && StringUtils.hasText(task.getTaskId())) {
                merged.putIfAbsent(task.getTaskId(), task);
            }
        }
        Comparator<AiCaseMaterializationTask> comparator = Comparator
                .comparing((AiCaseMaterializationTask task) -> task.getQueuedAt(), Comparator.nullsLast(Long::compareTo))
                .thenComparing(task -> task.getRetryCount(), Comparator.nullsLast(Integer::compareTo));
        return merged.values().stream()
                .sorted(comparator)
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

    private String toJson(AiCaseMaterializationTask task) {
        try {
            return objectMapper.writeValueAsString(task);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize AI case materialization task", ex);
        }
    }

    private AiCaseMaterializationTask fromJson(Object payload, String taskId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiCaseMaterializationTask.class);
        } catch (Exception ex) {
            log.warn("Skip invalid AI case materialization task, taskId={}", taskId, ex);
            return null;
        }
    }
}
