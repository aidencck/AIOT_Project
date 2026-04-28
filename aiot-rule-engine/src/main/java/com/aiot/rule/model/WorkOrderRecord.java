package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WorkOrderRecord {
    private String workOrderId;
    private String alarmId;
    private String deviceId;
    private String priority;
    private String status;
    private String assignee;
    private Long respondedAt;
    private Long resolvedAt;
    private String result;
    private Integer slaMinutes;
    private Long dueAt;
    private Boolean slaBreached;
    private Long createdAt;
    private Long updatedAt;
}
