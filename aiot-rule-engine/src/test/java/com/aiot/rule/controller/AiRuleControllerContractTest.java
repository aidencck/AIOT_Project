package com.aiot.rule.controller;

import com.aiot.common.config.GlobalResponseHandler;
import com.aiot.common.config.ResponseContractResolver;
import com.aiot.common.exception.GlobalExceptionHandler;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.service.RuleLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiRuleController.class, properties = {
        "AIOT_JWT_SECRET=0123456789abcdef0123456789abcdef"
})
@ContextConfiguration(classes = AiRuleControllerContractTest.MvcSliceConfig.class)
class AiRuleControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleLifecycleService ruleLifecycleService;

    @Test
    void shouldDraftRuleAndReturnWrappedResponse() throws Exception {
        when(ruleLifecycleService.draftRule(any(RuleDraftRequest.class)))
                .thenReturn(RuleDraftResponse.builder()
                        .ruleId("rule-1")
                        .conditionEventType("DEVICE_OFFLINE")
                        .conditionDeviceId("device-1")
                        .actionType("ALARM_CREATE")
                        .actionPayload("payload")
                        .status("DRAFT")
                        .build());

        mockMvc.perform(post("/api/v1/ai/rules/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requirement": "设备离线告警",
                                  "deviceId": "device-1",
                                  "eventType": "DEVICE_OFFLINE",
                                  "actionType": "ALARM_CREATE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ruleId").value("rule-1"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.actionType").value("ALARM_CREATE"));
    }

    @Test
    void shouldRejectDraftWhenRequirementMissing() throws Exception {
        mockMvc.perform(post("/api/v1/ai/rules/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "actionType": "ALARM_CREATE"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldApproveRuleAndReturnWrappedResponse() throws Exception {
        when(ruleLifecycleService.approveRule(eq("rule-1"), any(RuleApproveRequest.class)))
                .thenReturn(RuleDraftResponse.builder()
                        .ruleId("rule-1")
                        .conditionEventType("DEVICE_OFFLINE")
                        .conditionDeviceId("device-1")
                        .actionType("ALARM_CREATE")
                        .actionPayload("payload")
                        .status("APPROVED")
                        .build());

        mockMvc.perform(post("/api/v1/ai/rules/rule-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approver": "approver-1",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.ruleId").value("rule-1"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void shouldRejectApproveWhenApproverMissing() throws Exception {
        mockMvc.perform(post("/api/v1/ai/rules/rule-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void shouldReturnValidateFailedWhenApproveTargetIsNotDraft() throws Exception {
        when(ruleLifecycleService.approveRule(eq("rule-1"), any(RuleApproveRequest.class)))
                .thenThrow(new IllegalArgumentException("仅草稿状态可审批"));

        mockMvc.perform(post("/api/v1/ai/rules/rule-1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "approver": "approver-1",
                                  "comment": "同意"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
            AiRuleController.class,
            GlobalExceptionHandler.class,
            GlobalResponseHandler.class,
            ResponseContractResolver.class
    })
    static class MvcSliceConfig {
    }
}
