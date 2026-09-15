package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.config.AiPersistenceReadMode;
import com.aiot.rule.model.AiCaseRecord;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.aiot.rule.service.AiPersistenceReadModeResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class AiCaseRecordRepository {

    private static final String STORE_KEY = "aiot:ai:case-records";

    @Value("${aiot.ai.persistence.redis-ttl-days:7}")
    private long redisTtlDays;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiMysqlRecordWriter aiMysqlRecordWriter;
    private final AiMysqlRecordReader aiMysqlRecordReader;
    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final AiPersistenceReadModeResolver aiPersistenceReadModeResolver;
    private final AiPersistenceMetrics aiPersistenceMetrics;

    public AiCaseRecordRepository(RedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper,
                                  AiMysqlRecordWriter aiMysqlRecordWriter,
                                  AiMysqlRecordReader aiMysqlRecordReader,
                                  AiPersistenceMysqlProperties aiPersistenceMysqlProperties,
                                  AiPersistenceReadModeResolver aiPersistenceReadModeResolver,
                                  AiPersistenceMetrics aiPersistenceMetrics) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.aiMysqlRecordWriter = aiMysqlRecordWriter;
        this.aiMysqlRecordReader = aiMysqlRecordReader;
        this.aiPersistenceMysqlProperties = aiPersistenceMysqlProperties;
        this.aiPersistenceReadModeResolver = aiPersistenceReadModeResolver;
        this.aiPersistenceMetrics = aiPersistenceMetrics;
    }

    public void save(AiCaseRecord record) {
        boolean mysqlPersisted = aiMysqlRecordWriter.saveCase(record);
        writeRedisMirror(record, mysqlPersisted);
    }

    public List<AiCaseRecord> findBySceneType(String sceneType, int limit) {
        AiPersistenceReadMode configuredMode = aiPersistenceMysqlProperties.resolvedReadMode();
        var readDecision = aiPersistenceReadModeResolver.resolve();
        AiPersistenceReadMode readMode = readDecision.effectiveMode();
        if (readMode == AiPersistenceReadMode.MYSQL) {
            List<AiCaseRecord> mysqlRecords = aiMysqlRecordReader.findCasesBySceneType(sceneType, limit);
            aiPersistenceMetrics.recordRead("cases", configuredMode.name(), "mysql", !mysqlRecords.isEmpty());
            return mysqlRecords;
        }
        if (readMode == AiPersistenceReadMode.DUAL && readDecision.mysqlCutoverReady()) {
            List<AiCaseRecord> mysqlRecords = aiMysqlRecordReader.findCasesBySceneType(sceneType, limit);
            aiPersistenceMetrics.recordRead("cases", configuredMode.name(), "mysql", !mysqlRecords.isEmpty());
            if (!mysqlRecords.isEmpty()) {
                return mysqlRecords;
            }
            List<AiCaseRecord> redisRecords = findRedisBySceneType(sceneType, limit);
            aiPersistenceMetrics.recordFallback("cases", "mysql", "redis", !redisRecords.isEmpty());
            return redisRecords;
        }
        List<AiCaseRecord> redisRecords = findRedisBySceneType(sceneType, limit);
        aiPersistenceMetrics.recordRead("cases", configuredMode.name(), "redis", !redisRecords.isEmpty());
        if (!redisRecords.isEmpty() || readMode == AiPersistenceReadMode.REDIS) {
            return redisRecords;
        }
        List<AiCaseRecord> mysqlRecords = aiMysqlRecordReader.findCasesBySceneType(sceneType, limit);
        aiPersistenceMetrics.recordFallback("cases", "redis", "mysql", !mysqlRecords.isEmpty());
        return mysqlRecords;
    }

    private List<AiCaseRecord> findRedisBySceneType(String sceneType, int limit) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(STORE_KEY);
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(Objects::nonNull)
                .filter(item -> !StringUtils.hasText(sceneType) || sceneType.equalsIgnoreCase(item.getSceneType()))
                .sorted(Comparator.comparing(AiCaseRecord::getEffectivenessScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AiCaseRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());
    }

    private void writeRedisMirror(AiCaseRecord record, boolean mysqlPersisted) {
        try {
            redisTemplate.opsForHash().put(STORE_KEY, record.getCaseId(), toJson(record));
            redisTemplate.expire(STORE_KEY, redisTtlDays, TimeUnit.DAYS);
        } catch (Exception ex) {
            if (!mysqlPersisted || shouldRequireRedisMirror()) {
                throw new IllegalStateException("Failed to persist case record redis mirror", ex);
            }
            log.warn("Skip case redis mirror after mysql persisted, caseId={}", record.getCaseId(), ex);
        }
    }

    private boolean shouldRequireRedisMirror() {
        return aiPersistenceReadModeResolver.resolve().effectiveMode() == AiPersistenceReadMode.REDIS;
    }

    private String toJson(AiCaseRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize case record", ex);
        }
    }

    private AiCaseRecord fromJson(Object payload, String caseId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiCaseRecord.class);
        } catch (JsonProcessingException ex) {
            log.warn("Skip invalid case record, caseId={}", caseId, ex);
            return null;
        }
    }
}
