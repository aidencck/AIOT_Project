package com.aiot.rule.service;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.common.config.RedisUtils;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.model.RuleDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RuleLifecycleService {

    private static final String RULE_STORE_KEY = "aiot:rule:definitions";
    private static final String RULE_EXEC_IDEMPOTENCY_KEY_PREFIX = "aiot:rule:exec:idempotency:";
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisUtils redisUtils;
    private final RuleActionExecutor ruleActionExecutor;
    private final OpsClosureService opsClosureService;

    @Value("${aiot.rule.execution.idempotency-ttl-seconds:600}")
    private long executionIdempotencyTtlSeconds;

    public RuleLifecycleService(RedisTemplate<String, Object> redisTemplate,
                                ObjectMapper objectMapper,
                                RedisUtils redisUtils,
                                RuleActionExecutor ruleActionExecutor,
                                OpsClosureService opsClosureService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.redisUtils = redisUtils;
        this.ruleActionExecutor = ruleActionExecutor;
        this.opsClosureService = opsClosureService;
    }

    public RuleDraftResponse draftRule(RuleDraftRequest request) {
        String ruleId = UUID.randomUUID().toString();
        String eventType = StringUtils.hasText(request.getEventType())
                ? request.getEventType().trim().toUpperCase()
                : "DEVICE_OFFLINE";
        try {
            DeviceEventType.valueOf(eventType);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("非法事件类型: " + eventType);
        }
        String actionType = StringUtils.hasText(request.getActionType()) ? request.getActionType() : "ALARM_CREATE";
        String actionPayload = StringUtils.hasText(request.getActionPayload())
                ? request.getActionPayload()
                : (StringUtils.hasText(request.getRequirement()) ? request.getRequirement() : "设备离线时发送告警");

        RuleDefinition rule = RuleDefinition.builder()
                .ruleId(ruleId)
                .requirement(request.getRequirement())
                .conditionEventType(eventType)
                .conditionDeviceId(request.getDeviceId())
                .actionType(actionType)
                .actionPayload(actionPayload)
                .status("DRAFT")
                .build();
        saveRule(rule);
        return toResponse(rule);
    }

    public RuleDraftResponse approveRule(String ruleId, RuleApproveRequest request) {
        RuleDefinition rule = getRule(ruleId);
        rule.setStatus("APPROVED");
        rule.setApprovedBy(request.getApprover());
        saveRule(rule);
        return toResponse(rule);
    }

    public void executeByEvent(DeviceEvent event) {
        if (event == null) {
            return;
        }
        opsClosureService.refreshDeviceStatus(event);
        for (RuleDefinition rule : loadAllRules()) {
            if (!"APPROVED".equals(rule.getStatus())) {
                continue;
            }
            boolean matchEventType = event.getEventType() != null
                    && event.getEventType().name().equalsIgnoreCase(rule.getConditionEventType());
            if (!matchEventType) {
                continue;
            }
            if (StringUtils.hasText(rule.getConditionDeviceId())
                    && !rule.getConditionDeviceId().equals(event.getDeviceId())) {
                continue;
            }
            if (!tryAcquireExecution(rule, event)) {
                log.info("Skip duplicate rule execution, ruleId={}, eventId={}, deviceId={}",
                        rule.getRuleId(), event.getEventId(), event.getDeviceId());
                continue;
            }
            ruleActionExecutor.execute(rule, event);
        }
    }

    private RuleDefinition getRule(String ruleId) {
        Object payload = redisTemplate.opsForHash().get(RULE_STORE_KEY, ruleId);
        RuleDefinition rule = fromJson(payload, ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        return rule;
    }

    private void saveRule(RuleDefinition rule) {
        redisTemplate.opsForHash().put(RULE_STORE_KEY, rule.getRuleId(), toJson(rule));
    }

    private List<RuleDefinition> loadAllRules() {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(RULE_STORE_KEY);
        return entries.entrySet().stream()
                .map(entry -> fromJson(entry.getValue(), String.valueOf(entry.getKey())))
                .filter(java.util.Objects::nonNull)
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

    private boolean tryAcquireExecution(RuleDefinition rule, DeviceEvent event) {
        String rawEventId = StringUtils.hasText(event.getEventId())
                ? event.getEventId()
                : String.format("%s-%s-%s", event.getDeviceId(), event.getEventType(), event.getTimestamp());
        String key = RULE_EXEC_IDEMPOTENCY_KEY_PREFIX + rule.getRuleId() + ":" + rawEventId;
        return redisUtils.setIfAbsent(key, "1", executionIdempotencyTtlSeconds, TimeUnit.SECONDS);
    }

    private RuleDraftResponse toResponse(RuleDefinition rule) {
        return RuleDraftResponse.builder()
                .ruleId(rule.getRuleId())
                .conditionEventType(rule.getConditionEventType())
                .conditionDeviceId(rule.getConditionDeviceId())
                .actionType(rule.getActionType())
                .actionPayload(rule.getActionPayload())
                .status(rule.getStatus())
                .build();
    }
}
