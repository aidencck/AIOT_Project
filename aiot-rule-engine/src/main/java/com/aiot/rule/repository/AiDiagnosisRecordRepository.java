package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.config.AiPersistenceReadMode;
import com.aiot.rule.service.AiPersistenceReadModeResolver;
import com.aiot.rule.model.AiDiagnosisRecord;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class AiDiagnosisRecordRepository {

    private static final String STORE_KEY = "aiot:ai:diagnosis-records";

    @Value("${aiot.ai.persistence.redis-ttl-days:7}")
    private long redisTtlDays;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiMysqlRecordWriter aiMysqlRecordWriter;
    private final AiMysqlRecordReader aiMysqlRecordReader;
    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final AiPersistenceReadModeResolver aiPersistenceReadModeResolver;
    private final AiPersistenceMetrics aiPersistenceMetrics;

    public AiDiagnosisRecordRepository(RedisTemplate<String, Object> redisTemplate,
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

    public void save(AiDiagnosisRecord record) {
        boolean mysqlPersisted = aiMysqlRecordWriter.saveDiagnosis(record);
        writeRedisMirror(record, mysqlPersisted);
    }

    public AiDiagnosisRecord findById(String diagnosisId) {
        AiPersistenceReadMode configuredMode = aiPersistenceMysqlProperties.resolvedReadMode();
        var readDecision = aiPersistenceReadModeResolver.resolve();
        AiPersistenceReadMode readMode = readDecision.effectiveMode();
        if (readMode == AiPersistenceReadMode.MYSQL) {
            AiDiagnosisRecord mysqlRecord = aiMysqlRecordReader.findDiagnosisById(diagnosisId);
            aiPersistenceMetrics.recordRead("diagnosis", configuredMode.name(), "mysql", mysqlRecord != null);
            return mysqlRecord;
        }
        if (readMode == AiPersistenceReadMode.DUAL && readDecision.mysqlCutoverReady()) {
            AiDiagnosisRecord mysqlRecord = aiMysqlRecordReader.findDiagnosisById(diagnosisId);
            aiPersistenceMetrics.recordRead("diagnosis", configuredMode.name(), "mysql", mysqlRecord != null);
            if (mysqlRecord != null) {
                return mysqlRecord;
            }
            AiDiagnosisRecord redisRecord = findRedisById(diagnosisId);
            aiPersistenceMetrics.recordFallback("diagnosis", "mysql", "redis", redisRecord != null);
            return redisRecord;
        }
        AiDiagnosisRecord redisRecord = findRedisById(diagnosisId);
        aiPersistenceMetrics.recordRead("diagnosis", configuredMode.name(), "redis", redisRecord != null);
        if (redisRecord != null || readMode == AiPersistenceReadMode.REDIS) {
            return redisRecord;
        }
        AiDiagnosisRecord mysqlRecord = aiMysqlRecordReader.findDiagnosisById(diagnosisId);
        aiPersistenceMetrics.recordFallback("diagnosis", "redis", "mysql", mysqlRecord != null);
        return mysqlRecord;
    }

    private AiDiagnosisRecord findRedisById(String diagnosisId) {
        Object payload = redisTemplate.opsForHash().get(STORE_KEY, (Object) diagnosisId);
        return fromJson(payload, diagnosisId);
    }

    private void writeRedisMirror(AiDiagnosisRecord record, boolean mysqlPersisted) {
        try {
            redisTemplate.opsForHash().put(STORE_KEY, (Object) record.getDiagnosisId(), (Object) toJson(record));
            redisTemplate.expire(STORE_KEY, redisTtlDays, TimeUnit.DAYS);
        } catch (Exception ex) {
            if (!mysqlPersisted || shouldRequireRedisMirror()) {
                throw new IllegalStateException("Failed to persist diagnosis record redis mirror", ex);
            }
            log.warn("Skip diagnosis redis mirror after mysql persisted, diagnosisId={}", record.getDiagnosisId(), ex);
        }
    }

    private boolean shouldRequireRedisMirror() {
        return aiPersistenceReadModeResolver.resolve().effectiveMode() == AiPersistenceReadMode.REDIS;
    }

    private String toJson(AiDiagnosisRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize diagnosis record", ex);
        }
    }

    private AiDiagnosisRecord fromJson(Object payload, String diagnosisId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiDiagnosisRecord.class);
        } catch (JsonProcessingException ex) {
            log.warn("Skip invalid diagnosis record, diagnosisId={}", diagnosisId, ex);
            return null;
        }
    }
}
