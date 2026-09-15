package com.aiot.rule.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiControlPlaneDrainReportSummary {
    private Boolean exists;
    private String path;
    private String source;
    private Long executedAt;
    private String operator;
    private Boolean dryRun;
    private Boolean accepted;
    private Integer batchSize;
    private List<String> requestedStores;
    private Boolean controlPlaneRedisDrainCompleted;
    private String message;
    private Integer mysqlWriteOutboxRedisPending;
    private Integer mysqlWriteOutboxMysqlWritten;
    private Integer mysqlWriteOutboxRedisRemaining;
    private Integer caseMaterializationRedisPending;
    private Integer caseMaterializationMysqlWritten;
    private Integer caseMaterializationRedisRemaining;
}
