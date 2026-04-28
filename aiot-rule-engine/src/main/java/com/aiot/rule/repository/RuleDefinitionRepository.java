package com.aiot.rule.repository;

import com.aiot.rule.model.RuleDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class RuleDefinitionRepository {

    private static final String RULE_STORE_KEY = "aiot:rule:definitions";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public RuleDefinitionRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(RuleDefinition rule) {
        redisTemplate.opsForHash().put(RULE_STORE_KEY, rule.getRuleId(), toJson(rule));
    }

    public RuleDefinition findById(String ruleId) {
        Object payload = redisTemplate.opsForHash().get(RULE_STORE_KEY, ruleId);
        return fromJson(payload, ruleId);
    }

    public List<RuleDefinition> findAll() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULE_STORE_KEY);
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(Objects::nonNull)
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
