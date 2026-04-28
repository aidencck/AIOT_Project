package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleDefinition {
    private String ruleId;
    private String requirement;
    private String conditionEventType;
    private String conditionDeviceId;
    private String actionType;
    private String actionPayload;
    private String status;
    private String approvedBy;
}
