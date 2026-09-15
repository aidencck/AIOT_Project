package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiDiagnosisRequest;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.security.WebhookUrlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RuleActionExecutorTest {

    private ObjectMapper objectMapper;
    private OpsClosureService opsClosureService;
    private AiDiagnosisService aiDiagnosisService;
    private RuleActionExecutor ruleActionExecutor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        opsClosureService = mock(OpsClosureService.class);
        aiDiagnosisService = mock(AiDiagnosisService.class);
        Executor synchronousExecutor = Runnable::run;
        // WebhookUrlValidator 为 mock：validate 默认 doNothing，不会发起真实校验
        WebhookUrlValidator webhookUrlValidator = mock(WebhookUrlValidator.class);
        ruleActionExecutor = new RuleActionExecutor(objectMapper, opsClosureService, aiDiagnosisService, synchronousExecutor, webhookUrlValidator);
    }

    @Test
    void shouldDefaultEmptyActionTypeToAlertLogAndInvokeClosure() {
        RuleDefinition rule = rule("", "default-payload");
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        verify(opsClosureService).createAlarmAndWorkOrder(rule, event, "default-payload");
    }

    @Test
    void shouldNormalizeLowercaseAlarmCreateAndInvokeClosure() {
        RuleDefinition rule = rule("alarm_create", "payload");
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        verify(opsClosureService).createAlarmAndWorkOrder(rule, event, "payload");
    }

    @Test
    void shouldInvokeClosureForExplicitAlertLogAction() {
        RuleDefinition rule = rule("ALERT_LOG", "payload");
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        verify(opsClosureService).createAlarmAndWorkOrder(rule, event, "payload");
    }

    @Test
    void shouldMapDeviceOfflineToOfflineFlapAndDiagnose() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        ArgumentCaptor<AiDiagnosisRequest> captor = ArgumentCaptor.forClass(AiDiagnosisRequest.class);
        verify(aiDiagnosisService).diagnose(captor.capture());
        assertThat(captor.getValue().getDeviceId()).isEqualTo("device-1");
        assertThat(captor.getValue().getEventId()).isEqualTo("event-1");
        assertThat(captor.getValue().getSceneType()).isEqualTo("OFFLINE_FLAP");
    }

    @Test
    void shouldMapShadowDesiredUpdatedToShadowDiff() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.SHADOW_DESIRED_UPDATED);

        ruleActionExecutor.execute(rule, event);

        ArgumentCaptor<AiDiagnosisRequest> captor = ArgumentCaptor.forClass(AiDiagnosisRequest.class);
        verify(aiDiagnosisService).diagnose(captor.capture());
        assertThat(captor.getValue().getSceneType()).isEqualTo("SHADOW_DIFF");
    }

    @Test
    void shouldMapShadowReportedUpdatedToShadowDiff() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.SHADOW_REPORTED_UPDATED);

        ruleActionExecutor.execute(rule, event);

        ArgumentCaptor<AiDiagnosisRequest> captor = ArgumentCaptor.forClass(AiDiagnosisRequest.class);
        verify(aiDiagnosisService).diagnose(captor.capture());
        assertThat(captor.getValue().getSceneType()).isEqualTo("SHADOW_DIFF");
    }

    @Test
    void shouldMapProvisionFailedToProvisionFailure() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.DEVICE_PROVISION_FAILED);

        ruleActionExecutor.execute(rule, event);

        ArgumentCaptor<AiDiagnosisRequest> captor = ArgumentCaptor.forClass(AiDiagnosisRequest.class);
        verify(aiDiagnosisService).diagnose(captor.capture());
        assertThat(captor.getValue().getSceneType()).isEqualTo("PROVISION_FAILURE");
    }

    @Test
    void shouldMapProvisionRejectedToProvisionFailure() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.DEVICE_PROVISION_REJECTED);

        ruleActionExecutor.execute(rule, event);

        ArgumentCaptor<AiDiagnosisRequest> captor = ArgumentCaptor.forClass(AiDiagnosisRequest.class);
        verify(aiDiagnosisService).diagnose(captor.capture());
        assertThat(captor.getValue().getSceneType()).isEqualTo("PROVISION_FAILURE");
    }

    @Test
    void shouldSkipDiagnosisForUnsupportedEventType() {
        RuleDefinition rule = rule("AI_DIAGNOSE", null);
        DeviceEvent event = event(DeviceEventType.DEVICE_ONLINE);

        ruleActionExecutor.execute(rule, event);

        verify(aiDiagnosisService, never()).diagnose(any(AiDiagnosisRequest.class));
    }

    @Test
    void shouldSkipWebhookWhenPayloadEmpty() {
        RuleDefinition rule = rule("WEBHOOK_POST", "");
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        verify(opsClosureService, never()).createAlarmAndWorkOrder(any(), any(), any());
        verify(aiDiagnosisService, never()).diagnose(any(AiDiagnosisRequest.class));
    }

    @Test
    void shouldSkipWebhookWhenPayloadHasInvalidUrl() {
        RuleDefinition rule = rule("WEBHOOK_POST", "{\"noUrl\":\"x\"}");
        DeviceEvent event = event(DeviceEventType.DEVICE_OFFLINE);

        ruleActionExecutor.execute(rule, event);

        verify(opsClosureService, never()).createAlarmAndWorkOrder(any(), any(), any());
        verify(aiDiagnosisService, never()).diagnose(any(AiDiagnosisRequest.class));
    }

    private RuleDefinition rule(String actionType, String actionPayload) {
        return RuleDefinition.builder()
                .ruleId("rule-1")
                .conditionEventType("DEVICE_OFFLINE")
                .conditionDeviceId("device-1")
                .actionType(actionType)
                .actionPayload(actionPayload)
                .status("APPROVED")
                .build();
    }

    private DeviceEvent event(DeviceEventType eventType) {
        return DeviceEvent.builder()
                .eventId("event-1")
                .eventType(eventType)
                .deviceId("device-1")
                .timestamp(123456789L)
                .build();
    }
}
