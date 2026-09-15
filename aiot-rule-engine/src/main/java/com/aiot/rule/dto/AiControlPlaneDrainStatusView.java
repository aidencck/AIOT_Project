package com.aiot.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiControlPlaneDrainStatusView {
    private AiControlPlaneDrainExecutionSummary lastExecution;
    private AiControlPlaneDrainReportSummary report;
}
