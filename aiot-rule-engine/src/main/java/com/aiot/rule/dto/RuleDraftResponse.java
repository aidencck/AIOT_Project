package com.aiot.rule.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RuleDraftResponse {
    private String ruleId;
    private String conditionEventType;
    private String conditionDeviceId;
    private String actionType;
    private String actionPayload;
    private String status;
}
