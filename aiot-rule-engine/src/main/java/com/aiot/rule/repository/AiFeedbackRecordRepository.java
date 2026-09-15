package com.aiot.rule.repository;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.config.AiPersistenceReadMode;
import com.aiot.rule.model.AiFeedbackRecord;
import com.aiot.rule.service.AiPersistenceMetrics;
import com.aiot.rule.service.AiPersistenceReadModeResolver;
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
public class AiFeedbackRecordRepository {

    private static final String STORE_KEY = "aiot:ai:feedback-records";

    @Value("${aiot.ai.persistence.redis-ttl-days:7}")
    private long redisTtlDays;

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AiMysqlRecordWriter aiMysqlRecordWriter;
    private final AiMysqlRecordReader aiMysqlRecordReader;
    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final AiPersistenceReadModeResolver aiPersistenceReadModeResolver;
    private final AiPersistenceMetrics aiPersistenceMetrics;

    public AiFeedbackRecordRepository(RedisTemplate<String, Object> redisTemplate,
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

    public void save(AiFeedbackRecord record) {
        boolean mysqlPersisted = aiMysqlRecordWriter.saveFeedback(record);
        writeRedisMirror(record, mysqlPersisted);
    }

    private String toJson(AiFeedbackRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize feedback record", ex);
        }
    }

    public AiFeedbackRecord findById(String feedbackId) {
        AiPersistenceReadMode configuredMode = aiPersistenceMysqlProperties.resolvedReadMode();
        var readDecision = aiPersistenceReadModeResolver.resolve();
        AiPersistenceReadMode readMode = readDecision.effectiveMode();
        if (readMode == AiPersistenceReadMode.MYSQL) {
            AiFeedbackRecord mysqlRecord = aiMysqlRecordReader.findFeedbackById(feedbackId);
            aiPersistenceMetrics.recordRead("feedback", configuredMode.name(), "mysql", mysqlRecord != null);
            return mysqlRecord;
        }
        if (readMode == AiPersistenceReadMode.DUAL && readDecision.mysqlCutoverReady()) {
            AiFeedbackRecord mysqlRecord = aiMysqlRecordReader.findFeedbackById(feedbackId);
            aiPersistenceMetrics.recordRead("feedback", configuredMode.name(), "mysql", mysqlRecord != null);
            if (mysqlRecord != null) {
                return mysqlRecord;
            }
            AiFeedbackRecord redisRecord = findRedisById(feedbackId);
            aiPersistenceMetrics.recordFallback("feedback", "mysql", "redis", redisRecord != null);
            return redisRecord;
        }
        AiFeedbackRecord redisRecord = findRedisById(feedbackId);
        aiPersistenceMetrics.recordRead("feedback", configuredMode.name(), "redis", redisRecord != null);
        if (redisRecord != null || readMode == AiPersistenceReadMode.REDIS) {
            return redisRecord;
        }
        AiFeedbackRecord mysqlRecord = aiMysqlRecordReader.findFeedbackById(feedbackId);
        aiPersistenceMetrics.recordFallback("feedback", "redis", "mysql", mysqlRecord != null);
        return mysqlRecord;
    }

    private AiFeedbackRecord findRedisById(String feedbackId) {
        Object payload = redisTemplate.opsForHash().get(STORE_KEY, (Object) feedbackId);
        return fromJson(payload, feedbackId);
    }

    private void writeRedisMirror(AiFeedbackRecord record, boolean mysqlPersisted) {
        try {
            redisTemplate.opsForHash().put(STORE_KEY, (Object) record.getFeedbackId(), (Object) toJson(record));
            redisTemplate.expire(STORE_KEY, redisTtlDays, TimeUnit.DAYS);
        } catch (Exception ex) {
            if (!mysqlPersisted || shouldRequireRedisMirror()) {
                throw new IllegalStateException("Failed to persist feedback record redis mirror", ex);
            }
            log.warn("Skip feedback redis mirror after mysql persisted, feedbackId={}", record.getFeedbackId(), ex);
        }
    }

    private boolean shouldRequireRedisMirror() {
        return aiPersistenceReadModeResolver.resolve().effectiveMode() == AiPersistenceReadMode.REDIS;
    }

    private AiFeedbackRecord fromJson(Object payload, String feedbackId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, AiFeedbackRecord.class);
        } catch (JsonProcessingException ex) {
            log.warn("Skip invalid feedback record, feedbackId={}", feedbackId, ex);
            return null;
        }
    }
}
