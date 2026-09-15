package com.aiot.rule.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditRecord {
    private String auditId;
    private String eventType;
    private String operator;
    private String targetId;
    private String details;
    private String traceId;
    private Long createdAt;
}
