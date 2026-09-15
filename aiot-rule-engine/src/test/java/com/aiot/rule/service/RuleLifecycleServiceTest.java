package com.aiot.rule.service;

import com.aiot.common.config.RedisUtils;
import com.aiot.rule.dto.RuleApproveRequest;
import com.aiot.rule.dto.RuleDraftRequest;
import com.aiot.rule.dto.RuleDraftResponse;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.repository.RuleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RuleLifecycleServiceTest {

    private RuleDefinitionRepository ruleDefinitionRepository;
    private RuleLifecycleService ruleLifecycleService;

    @BeforeEach
    void setUp() {
        ruleDefinitionRepository = mock(RuleDefinitionRepository.class);
        ruleLifecycleService = new RuleLifecycleService(
                ruleDefinitionRepository,
                mock(RedisUtils.class),
                mock(RuleActionExecutor.class),
                mock(OpsClosureService.class)
        );
    }

    @Test
    void shouldApproveDraftAndRecordMetadata() {
        RuleDefinition rule = draftDefinition("rule-1");
        when(ruleDefinitionRepository.findById("rule-1")).thenReturn(rule);
        RuleApproveRequest request = new RuleApproveRequest();
        request.setApprover("approver-1");
        request.setComment("同意");

        RuleDraftResponse response = ruleLifecycleService.approveRule("rule-1", request);

        assertEquals("APPROVED", response.getStatus());
        assertEquals("APPROVED", rule.getStatus());
        assertEquals("approver-1", rule.getApprovedBy());
        assertEquals("同意", rule.getComment());
        assertNotNull(rule.getApprovedAt());
        verify(ruleDefinitionRepository).save(rule);
    }

    @Test
    void shouldRejectApproveWhenRuleIsNotDraft() {
        RuleDefinition rule = draftDefinition("rule-1");
        rule.setStatus("APPROVED");
        when(ruleDefinitionRepository.findById("rule-1")).thenReturn(rule);
        RuleApproveRequest request = new RuleApproveRequest();
        request.setApprover("approver-1");

        assertThrows(IllegalArgumentException.class, () -> ruleLifecycleService.approveRule("rule-1", request));
        verify(ruleDefinitionRepository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void shouldRejectDraftAndRecordReason() {
        RuleDefinition rule = draftDefinition("rule-1");
        when(ruleDefinitionRepository.findById("rule-1")).thenReturn(rule);

        RuleDraftResponse response = ruleLifecycleService.rejectRule("rule-1", "原因不合理");

        assertEquals("REJECTED", response.getStatus());
        assertEquals("REJECTED", rule.getStatus());
        assertEquals("原因不合理", rule.getRejectReason());
        verify(ruleDefinitionRepository).save(rule);
    }

    @Test
    void shouldRejectRejectionWhenRuleIsNotDraft() {
        RuleDefinition rule = draftDefinition("rule-1");
        rule.setStatus("REJECTED");
        when(ruleDefinitionRepository.findById("rule-1")).thenReturn(rule);

        assertThrows(IllegalArgumentException.class, () -> ruleLifecycleService.rejectRule("rule-1", "原因"));
        verify(ruleDefinitionRepository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void shouldNormalizeActionTypeAndSaveDraft() {
        RuleDraftRequest request = new RuleDraftRequest();
        request.setRequirement("设备离线告警");
        request.setActionType("alarm_create");

        RuleDraftResponse response = ruleLifecycleService.draftRule(request);

        assertEquals("ALARM_CREATE", response.getActionType());
        ArgumentCaptor<RuleDefinition> captor = ArgumentCaptor.forClass(RuleDefinition.class);
        verify(ruleDefinitionRepository).save(captor.capture());
        assertEquals("ALARM_CREATE", captor.getValue().getActionType());
        assertEquals("DRAFT", captor.getValue().getStatus());
    }

    @Test
    void shouldRejectUnknownActionTypeInDraft() {
        RuleDraftRequest request = new RuleDraftRequest();
        request.setRequirement("设备离线告警");
        request.setActionType("UNKNOWN_ACTION");

        assertThrows(IllegalArgumentException.class, () -> ruleLifecycleService.draftRule(request));
        verify(ruleDefinitionRepository, never()).save(any(RuleDefinition.class));
    }

    @Test
    void shouldListRulesByStatus() {
        when(ruleDefinitionRepository.findByStatus("DRAFT")).thenReturn(List.of(draftDefinition("rule-1")));

        List<RuleDraftResponse> rules = ruleLifecycleService.listRules("DRAFT");

        assertEquals(1, rules.size());
        assertEquals("rule-1", rules.get(0).getRuleId());
    }

    private RuleDefinition draftDefinition(String ruleId) {
        return RuleDefinition.builder()
                .ruleId(ruleId)
                .requirement("requirement")
                .conditionEventType("DEVICE_OFFLINE")
                .conditionDeviceId("dev-1")
                .actionType("ALARM_CREATE")
                .actionPayload("payload")
                .status("DRAFT")
                .build();
    }
}
