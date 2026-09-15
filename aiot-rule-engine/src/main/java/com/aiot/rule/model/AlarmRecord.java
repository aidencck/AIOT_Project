package com.aiot.rule.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRecord {
    private String alarmId;
    private String ruleId;
    private String eventId;
    private String deviceId;
    private String globalDeviceId;
    private String authIdentity;
    private String deviceSn;
    private String eventType;
    private String level;
    private String status;
    private Long occurredAt;
    private Long acknowledgedAt;
    private String acknowledgedBy;
    private Long resolvedAt;
    private String resolvedBy;
    private Long createdAt;
    private Long updatedAt;
}
