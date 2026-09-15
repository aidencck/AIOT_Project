package com.aiot.rule.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPersistenceStatusResponse {
    private Boolean mysqlEnabled;
    private String readMode;
    private String configuredReadMode;
    private Boolean mysqlReady;
    private Boolean mysqlCutoverReady;
    private String mysqlCutoverBlockReason;
    private Boolean backfillManifestExists;
    private String backfillManifestPath;
    private Integer backfillSourceCount;
    private Integer backfillScannedTotal;
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
    private Boolean mysqlWriteOutboxReplayEnabled;
    private Integer mysqlWriteOutboxReplayBatchSize;
    private Long mysqlWriteOutboxReplayFixedDelayMs;
    private Integer mysqlWriteOutboxPendingCount;
    private Long mysqlWriteOutboxOldestAgeSeconds;
    private String caseMaterializationStoreMode;
    private Integer caseMaterializationLegacyRedisPendingCount;
    private Boolean caseMaterializationReplayEnabled;
    private Integer caseMaterializationReplayBatchSize;
    private Long caseMaterializationReplayFixedDelayMs;
    private Integer caseMaterializationPendingCount;
    private Long caseMaterializationOldestAgeSeconds;
    private Boolean controlPlaneRedisDrainCompleted;
    private AiBusinessLiveFlowStatusView businessLiveFlowStatus;
    private Boolean businessLiveFlowReportExists;
    private String businessLiveFlowReportPath;
    private Long businessLiveFlowVerifiedAt;
    private String businessLiveFlowScene;
    private Boolean businessLiveFlowSuccess;
    private String businessLiveFlowDeviceId;
    private String businessLiveFlowGlobalDeviceId;
    private String businessLiveFlowAuthIdentity;
    private String businessLiveFlowDeviceSn;
    private String businessLiveFlowEventId;
    private String businessLiveFlowDiagnosisId;
    private String businessLiveFlowFeedbackId;
    private String businessLiveFlowCaseId;
    private Integer businessLiveFlowMysqlWriteOutboxCount;
    private Integer businessLiveFlowCaseMaterializationTaskCount;
    private Boolean businessLiveFlowDiagnosisMirrorExists;
    private Boolean businessLiveFlowFeedbackMirrorExists;
    private Boolean businessLiveFlowCaseMirrorExists;
    private AiControlPlaneDrainStatusView controlPlaneDrainStatus;
    private Long controlPlaneLastDrainAt;
    private String controlPlaneLastDrainOperator;
    private Boolean controlPlaneLastDrainDryRun;
    private Boolean controlPlaneLastDrainAccepted;
    private String controlPlaneLastDrainMessage;
}
