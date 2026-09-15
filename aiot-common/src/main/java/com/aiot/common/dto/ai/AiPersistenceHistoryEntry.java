package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistenceHistoryEntry {
    private String reportType;
    private Long occurredAt;
    private String reportPath;
    private String operator;
    private String scene;
    private Boolean success;
    private Boolean accepted;
    private Boolean dryRun;
    private String message;
}
