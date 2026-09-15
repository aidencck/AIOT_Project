package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiControlPlaneDrainSnapshot {
    private Boolean completed;
    private Long lastDrainAt;
    private String lastDrainOperator;
    private Boolean lastDrainDryRun;
    private Boolean lastDrainAccepted;
    private String lastDrainMessage;
    private Boolean reportExists;
    private String reportPath;
    private String reportSource;
    private Long reportExecutedAt;
    private String reportOperator;
    private Boolean reportDryRun;
    private Boolean reportAccepted;
    private Integer reportBatchSize;
    private Integer mysqlWriteOutboxRedisPending;
    private Integer mysqlWriteOutboxMysqlWritten;
    private Integer mysqlWriteOutboxRedisRemaining;
    private Integer caseMaterializationRedisPending;
    private Integer caseMaterializationMysqlWritten;
    private Integer caseMaterializationRedisRemaining;
    private List<AiPersistenceHistoryEntry> recentHistory;
}
