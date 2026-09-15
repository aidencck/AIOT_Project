package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistenceStatusSnapshot {
    private Boolean mysqlEnabled;
    private String readMode;
    private String configuredReadMode;
    private Boolean mysqlReady;
    private Boolean mysqlCutoverReady;
    private String mysqlCutoverBlockReason;
    private Boolean backfillManifestExists;
    private Integer backfillWrittenTotal;
    private Boolean consistencyReportExists;
    private Boolean consistencyPassed;
    private Integer consistencyTotalMismatch;
    private Boolean constraintsReportExists;
    private Boolean constraintsGatePassed;
    private Integer constraintsFailedCount;
    private Boolean migrationGateReportExists;
    private Boolean migrationGatePassed;
    private String mysqlWriteOutboxStoreMode;
    private Integer mysqlWriteOutboxLegacyRedisPendingCount;
    private Integer mysqlWriteOutboxPendingCount;
    private String caseMaterializationStoreMode;
    private Integer caseMaterializationLegacyRedisPendingCount;
    private Integer caseMaterializationPendingCount;
}
