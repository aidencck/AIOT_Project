package com.aiot.rule.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiControlPlaneDrainResponse {
    private Boolean accepted;
    private Boolean dryRun;
    private Integer batchSize;
    private List<String> requestedStores;
    private String mysqlWriteOutboxStoreMode;
    private String caseMaterializationStoreMode;
    private Integer mysqlWriteOutboxLegacyRedisBefore;
    private Integer caseMaterializationLegacyRedisBefore;
    private Integer mysqlWriteOutboxMigrated;
    private Integer caseMaterializationMigrated;
    private Integer mysqlWriteOutboxLegacyRedisAfter;
    private Integer caseMaterializationLegacyRedisAfter;
    private Boolean controlPlaneRedisDrainCompleted;
    private String reportPath;
    private String message;
    private Long executedAt;
    private String operator;
}
