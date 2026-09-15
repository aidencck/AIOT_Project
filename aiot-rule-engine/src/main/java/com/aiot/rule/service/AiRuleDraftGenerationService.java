package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.repository.RuleDefinitionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class AiRuleDraftGenerationService {

    private final RuleDefinitionRepository ruleDefinitionRepository;
    private final Executor executor;

    public AiRuleDraftGenerationService(RuleDefinitionRepository ruleDefinitionRepository,
                                        @Qualifier("applicationTaskExecutor") Executor executor) {
        this.ruleDefinitionRepository = ruleDefinitionRepository;
        this.executor = executor;
    }

    public void generateDraftIfEligible(AiDiagnosisRequest request, AiDiagnosisResponse response) {
        if (!Boolean.TRUE.equals(response.getRuleDraftable())) {
            return;
        }
        String eventType = mapEventType(request.getSceneType());
        if (eventType == null) {
            return;
        }
        boolean draftExists = ruleDefinitionRepository.findAll().stream()
                .anyMatch(rule -> "DRAFT".equals(rule.getStatus())
                        && Objects.equals(request.getDeviceId(), rule.getConditionDeviceId())
                        && Objects.equals(eventType, rule.getConditionEventType()));
        if (draftExists) {
            log.info("规则草案已存在，跳过重复生成, deviceId={}, eventType={}", request.getDeviceId(), eventType);
            return;
        }
        List<String> recommendedActions = response.getRecommendedActions();
        String actionPayload = recommendedActions == null || recommendedActions.isEmpty()
                ? response.getSummary()
                : String.join("；", recommendedActions);
        RuleDefinition ruleDefinition = RuleDefinition.builder()
                .ruleId(UUID.randomUUID().toString())
                .requirement(response.getSummary())
                .conditionEventType(eventType)
                .conditionDeviceId(request.getDeviceId())
                .actionType("ALARM_CREATE")
                .actionPayload(actionPayload)
                .status("DRAFT")
                .approvedBy(null)
                .diagnosisId(response.getDiagnosisId())
                .build();
        ruleDefinitionRepository.save(ruleDefinition);
        log.info("规则草案生成成功, ruleId={}, deviceId={}, eventType={}", ruleDefinition.getRuleId(), request.getDeviceId(), eventType);
    }

    private String mapEventType(String sceneType) {
        String normalized = sceneType == null ? "" : sceneType.trim().toUpperCase();
        return switch (normalized) {
            case "OFFLINE_FLAP" -> "DEVICE_OFFLINE";
            case "SHADOW_DIFF" -> "SHADOW_DESIRED_UPDATED";
            case "PROVISION_FAILURE" -> "DEVICE_PROVISION_FAILED";
            default -> null;
        };
    }
}
