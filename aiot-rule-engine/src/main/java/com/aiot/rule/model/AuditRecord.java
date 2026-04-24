package com.aiot.rule.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuditRecord {
    private String auditId;
    private String eventType;
    private String operator;
    private String targetId;
    private String details;
    private String traceId;
    private Long createdAt;
}
