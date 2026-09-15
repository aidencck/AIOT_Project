package com.aiot.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiControlPlaneDrainExecutionSummary {
    private Long executedAt;
    private String operator;
    private Boolean dryRun;
    private Boolean accepted;
    private String message;
}
