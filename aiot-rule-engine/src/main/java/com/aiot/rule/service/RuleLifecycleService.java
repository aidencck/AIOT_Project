package com.aiot.rule.service;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.common.config.RedisUtils;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.repository.RuleDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RuleLifecycleService {

    private static final String RULE_EXEC_IDEMPOTENCY_KEY_PREFIX = "aiot:rule:exec:idempotency:";
    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final RedisUtils redisUtils;
    private final RuleActionExecutor ruleActionExecutor;
    private final OpsClosureService opsClosureService;

    @Value("${aiot.rule.execution.idempotency-ttl-seconds:600}")
    private long executionIdempotencyTtlSeconds;

    public RuleLifecycleService(RuleDefinitionRepository ruleDefinitionRepository,
                                RedisUtils redisUtils,
                                RuleActionExecutor ruleActionExecutor,
                                OpsClosureService opsClosureService) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
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
        RuleDefinition rule = ruleDefinitionRepository.findById(ruleId);
        if (rule == null) {
            throw new IllegalArgumentException("Rule not found: " + ruleId);
        }
        return rule;
    }

    private void saveRule(RuleDefinition rule) {
        ruleDefinitionRepository.save(rule);
    }

    private List<RuleDefinition> loadAllRules() {
        return ruleDefinitionRepository.findAll();
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
