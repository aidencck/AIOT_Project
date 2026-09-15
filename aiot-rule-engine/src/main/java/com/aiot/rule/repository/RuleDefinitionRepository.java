package com.aiot.rule.repository;

import com.aiot.rule.model.RuleDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RuleDefinitionRepository {

    private static final String RULE_STORE_KEY = "aiot:rule:definitions";
    private static final String RULE_INDEX_KEY_PREFIX = "aiot:rule:definitions:index:event-type:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RuleDefinitionRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(RuleDefinition rule) {
        RuleDefinition old = findById(rule.getRuleId());
        if (old != null && !Objects.equals(old.getConditionEventType(), rule.getConditionEventType())) {
            redisTemplate.opsForSet().remove(RULE_INDEX_KEY_PREFIX + old.getConditionEventType(), old.getRuleId());
        }
        redisTemplate.opsForHash().put(RULE_STORE_KEY, rule.getRuleId(), toJson(rule));
        redisTemplate.opsForSet().add(RULE_INDEX_KEY_PREFIX + rule.getConditionEventType(), rule.getRuleId());
    }

    public RuleDefinition findById(String ruleId) {
        Object payload = redisTemplate.opsForHash().get(RULE_STORE_KEY, ruleId);
        return fromJson(payload, ruleId);
    }

    public void deleteById(String ruleId) {
        RuleDefinition old = findById(ruleId);
        if (old != null && StringUtils.hasText(old.getConditionEventType())) {
            redisTemplate.opsForSet().remove(RULE_INDEX_KEY_PREFIX + old.getConditionEventType(), old.getRuleId());
        }
        redisTemplate.opsForHash().delete(RULE_STORE_KEY, ruleId);
    }

    public List<RuleDefinition> findByEventType(String eventType) {
        if (eventType == null) {
            return Collections.emptyList();
        }
        Set<Object> ruleIds = redisTemplate.opsForSet().members(RULE_INDEX_KEY_PREFIX + eventType);
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return ruleIds.stream()
                .map(id -> fromJson(redisTemplate.opsForHash().get(RULE_STORE_KEY, id), String.valueOf(id)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<RuleDefinition> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULE_STORE_KEY);
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<RuleDefinition> findByStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return findAll();
        }
        return findAll().stream()
                .filter(rule -> status.equals(rule.getStatus()))
                .collect(Collectors.toList());
    }

    private String toJson(RuleDefinition rule) {
        try {
            return objectMapper.writeValueAsString(rule);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize rule: " + rule.getRuleId(), e);
        }
    }

    private RuleDefinition fromJson(Object payload, String ruleId) {
        if (!(payload instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RuleDefinition.class);
        } catch (JsonProcessingException e) {
            log.warn("Skip invalid rule payload, ruleId={}", ruleId, e);
            return null;
        }
    }
}
