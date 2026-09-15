package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.dto.ai.AiDiagnosisResponse;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.repository.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRuleDraftGenerationServiceTest {

    private RuleDefinitionRepository ruleDefinitionRepository;
    private AiRuleDraftGenerationService service;

    @BeforeEach
    void setUp() {
        ruleDefinitionRepository = mock(RuleDefinitionRepository.class);
        service = new AiRuleDraftGenerationService(ruleDefinitionRepository, mock(Executor.class));
    }

    @Test
    void shouldGenerateDraftAndBackfillDiagnosisId() {
        when(ruleDefinitionRepository.findAll()).thenReturn(List.of());

        service.generateDraftIfEligible(request(), response());

        ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
        verify(ruleDefinitionRepository).save(captor.capture());
        RuleDefinition saved = captor.getValue();
        assertEquals("diag-1", saved.getDiagnosisId());
        assertEquals("DEVICE_OFFLINE", saved.getConditionEventType());
        assertEquals("dev-1", saved.getConditionDeviceId());
        assertEquals("DRAFT", saved.getStatus());
    }

    @Test
    void shouldSkipWhenNotDraftable() {
        service.generateDraftIfEligible(request(), AiDiagnosisResponse.builder()
                .diagnosisId("diag-1")
                .ruleDraftable(false)
                .build());

        verify(ruleDefinitionRepository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void shouldSkipWhenDraftAlreadyExists() {
        when(ruleDefinitionRepository.findAll()).thenReturn(List.of(RuleDefinition.builder()
                .ruleId("existing-rule")
                .status("DRAFT")
                .conditionDeviceId("dev-1")
                .conditionEventType("DEVICE_OFFLINE")
                .build()));

        service.generateDraftIfEligible(request(), response());

        verify(ruleDefinitionRepository, never()).save(any(RuleDefinition.class));
    }

    private AiDiagnosisRequest request() {
        AiDiagnosisRequest request = new AiDiagnosisRequest();
        request.setDeviceId("dev-1");
        request.setSceneType("OFFLINE_FLAP");
        return request;
    }

    private AiDiagnosisResponse response() {
        return AiDiagnosisResponse.builder()
                .diagnosisId("diag-1")
                .ruleDraftable(true)
                .summary("summary")
                .recommendedActions(List.of("act-1", "act-2"))
                .build();
    }
}
