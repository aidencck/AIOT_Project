package com.aiot.rule.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuleDefinition {
    private String ruleId;
    private String requirement;
    private String conditionEventType;
    private String conditionDeviceId;
    private String actionType;
    private String actionPayload;
    private String status;
    private String approvedBy;
    private String diagnosisId;
    private String comment;
    private Long approvedAt;
    private String rejectReason;
}
