package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiBusinessLiveFlowSnapshot;
import com.aiot.common.dto.ai.AiControlPlaneDrainSnapshot;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceStatusSnapshot;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class AiPersistenceAdminQueryService {

    private static final int DEFAULT_HISTORY_LIMIT = 10;

    private final AiPersistenceStatusService aiPersistenceStatusService;
    private final AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider;
    private final AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider;

    public AiPersistenceAdminQueryService(AiPersistenceStatusService aiPersistenceStatusService,
                                          AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider,
                                          AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider) {
        this.aiPersistenceStatusService = aiPersistenceStatusService;
        this.aiControlPlaneDrainStatusProvider = aiControlPlaneDrainStatusProvider;
        this.aiBusinessLiveFlowStatusProvider = aiBusinessLiveFlowStatusProvider;
    }

    public AiPersistenceAdminQueryResp getQuery() {
        AiPersistenceStatusResponse status = aiPersistenceStatusService.getStatus();
        var drainStatus = aiControlPlaneDrainStatusProvider.getStatus();
        var drainReport = drainStatus == null ? null : drainStatus.getReport();
        var lastDrain = drainStatus == null ? null : drainStatus.getLastExecution();
        var businessLiveFlowStatus = aiBusinessLiveFlowStatusProvider.getStatus();
        var businessLiveFlowReport = businessLiveFlowStatus == null ? null : businessLiveFlowStatus.getReport();
        return AiPersistenceAdminQueryResp.builder()
                .status(AiPersistenceStatusSnapshot.builder()
                        .mysqlEnabled(status.getMysqlEnabled())
                        .readMode(status.getReadMode())
                        .configuredReadMode(status.getConfiguredReadMode())
                        .mysqlReady(status.getMysqlReady())
                        .mysqlCutoverReady(status.getMysqlCutoverReady())
                        .mysqlCutoverBlockReason(status.getMysqlCutoverBlockReason())
                        .backfillManifestExists(status.getBackfillManifestExists())
                        .backfillWrittenTotal(status.getBackfillWrittenTotal())
                        .consistencyReportExists(status.getConsistencyReportExists())
                        .consistencyPassed(status.getConsistencyPassed())
                        .consistencyTotalMismatch(status.getConsistencyTotalMismatch())
                        .constraintsReportExists(status.getConstraintsReportExists())
                        .constraintsGatePassed(status.getConstraintsGatePassed())
                        .constraintsFailedCount(status.getConstraintsFailedCount())
                        .migrationGateReportExists(status.getMigrationGateReportExists())
                        .migrationGatePassed(status.getMigrationGatePassed())
                        .mysqlWriteOutboxStoreMode(status.getMysqlWriteOutboxStoreMode())
                        .mysqlWriteOutboxLegacyRedisPendingCount(status.getMysqlWriteOutboxLegacyRedisPendingCount())
                        .mysqlWriteOutboxPendingCount(status.getMysqlWriteOutboxPendingCount())
                        .caseMaterializationStoreMode(status.getCaseMaterializationStoreMode())
                        .caseMaterializationLegacyRedisPendingCount(status.getCaseMaterializationLegacyRedisPendingCount())
                        .caseMaterializationPendingCount(status.getCaseMaterializationPendingCount())
                        .build())
                .controlPlaneDrain(AiControlPlaneDrainSnapshot.builder()
                        .completed(status.getControlPlaneRedisDrainCompleted())
                        .lastDrainAt(lastDrain == null ? null : lastDrain.getExecutedAt())
                        .lastDrainOperator(lastDrain == null ? null : lastDrain.getOperator())
                        .lastDrainDryRun(lastDrain == null ? null : lastDrain.getDryRun())
                        .lastDrainAccepted(lastDrain == null ? null : lastDrain.getAccepted())
                        .lastDrainMessage(lastDrain == null ? null : lastDrain.getMessage())
                        .reportExists(drainReport == null ? Boolean.FALSE : drainReport.getExists())
                        .reportPath(drainReport == null ? null : drainReport.getPath())
                        .reportSource(drainReport == null ? null : drainReport.getSource())
                        .reportExecutedAt(drainReport == null ? null : drainReport.getExecutedAt())
                        .reportOperator(drainReport == null ? null : drainReport.getOperator())
                        .reportDryRun(drainReport == null ? null : drainReport.getDryRun())
                        .reportAccepted(drainReport == null ? null : drainReport.getAccepted())
                        .reportBatchSize(drainReport == null ? null : drainReport.getBatchSize())
                        .mysqlWriteOutboxRedisPending(drainReport == null ? null : drainReport.getMysqlWriteOutboxRedisPending())
                        .mysqlWriteOutboxMysqlWritten(drainReport == null ? null : drainReport.getMysqlWriteOutboxMysqlWritten())
                        .mysqlWriteOutboxRedisRemaining(drainReport == null ? null : drainReport.getMysqlWriteOutboxRedisRemaining())
                        .caseMaterializationRedisPending(drainReport == null ? null : drainReport.getCaseMaterializationRedisPending())
                        .caseMaterializationMysqlWritten(drainReport == null ? null : drainReport.getCaseMaterializationMysqlWritten())
                        .caseMaterializationRedisRemaining(drainReport == null ? null : drainReport.getCaseMaterializationRedisRemaining())
                        .recentHistory(aiControlPlaneDrainStatusProvider.getRecentHistory(DEFAULT_HISTORY_LIMIT))
                        .build())
                .businessLiveFlow(AiBusinessLiveFlowSnapshot.builder()
                        .reportExists(businessLiveFlowReport == null ? Boolean.FALSE : businessLiveFlowReport.getExists())
                        .reportPath(businessLiveFlowReport == null ? null : businessLiveFlowReport.getPath())
                        .verifiedAt(businessLiveFlowReport == null ? null : businessLiveFlowReport.getVerifiedAt())
                        .scene(businessLiveFlowReport == null ? null : businessLiveFlowReport.getScene())
                        .success(businessLiveFlowReport == null ? null : businessLiveFlowReport.getSuccess())
                        .deviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceId())
                        .globalDeviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getGlobalDeviceId())
                        .authIdentity(businessLiveFlowReport == null ? null : businessLiveFlowReport.getAuthIdentity())
                        .deviceSn(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceSn())
                        .eventId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getEventId())
                        .diagnosisId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisId())
                        .feedbackId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackId())
                        .caseId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseId())
                        .mysqlWriteOutboxCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getMysqlWriteOutboxCount())
                        .caseMaterializationTaskCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMaterializationTaskCount())
                        .diagnosisMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisMirrorExists())
                        .feedbackMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackMirrorExists())
                        .caseMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMirrorExists())
                        .recentHistory(aiBusinessLiveFlowStatusProvider.getRecentHistory(DEFAULT_HISTORY_LIMIT))
                        .build())
                .build();
    }
}
