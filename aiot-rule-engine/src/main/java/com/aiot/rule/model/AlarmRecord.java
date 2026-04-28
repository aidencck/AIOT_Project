package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlarmRecord {
    private String alarmId;
    private String ruleId;
    private String eventId;
    private String deviceId;
    private String eventType;
    private String level;
    private String status;
    private Long occurredAt;
    private Long acknowledgedAt;
    private String acknowledgedBy;
    private Long createdAt;
    private Long updatedAt;
}
