package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiPersistenceHistoryEntry;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.rule.dto.AiBusinessLiveFlowReportSummary;
import com.aiot.rule.dto.AiBusinessLiveFlowStatusView;
import com.aiot.rule.dto.AiControlPlaneDrainExecutionSummary;
import com.aiot.rule.dto.AiControlPlaneDrainReportSummary;
import com.aiot.rule.dto.AiControlPlaneDrainStatusView;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPersistenceAdminQueryServiceTest {

    @Test
    void shouldAssembleUnifiedAiPersistenceAdminQuery() {
        AiPersistenceStatusService statusService = mock(AiPersistenceStatusService.class);
        AiControlPlaneDrainStatusProvider drainStatusProvider = mock(AiControlPlaneDrainStatusProvider.class);
        AiBusinessLiveFlowStatusProvider businessLiveFlowStatusProvider = mock(AiBusinessLiveFlowStatusProvider.class);

        when(statusService.getStatus()).thenReturn(AiPersistenceStatusResponse.builder()
                .mysqlEnabled(true)
                .readMode("DUAL")
                .configuredReadMode("MYSQL")
                .mysqlReady(true)
                .mysqlCutoverReady(false)
                .mysqlCutoverBlockReason("migration gate not passed")
                .backfillManifestExists(true)
                .backfillWrittenTotal(17)
                .consistencyReportExists(true)
                .consistencyPassed(true)
                .consistencyTotalMismatch(0)
                .constraintsReportExists(true)
                .constraintsGatePassed(true)
                .constraintsFailedCount(0)
                .migrationGateReportExists(true)
                .migrationGatePassed(true)
                .mysqlWriteOutboxStoreMode("MYSQL_TABLE_PRIMARY")
                .mysqlWriteOutboxLegacyRedisPendingCount(0)
                .mysqlWriteOutboxPendingCount(2)
                .caseMaterializationStoreMode("MYSQL_TABLE_PRIMARY")
                .caseMaterializationLegacyRedisPendingCount(0)
                .caseMaterializationPendingCount(1)
                .controlPlaneRedisDrainCompleted(true)
                .build());

        when(drainStatusProvider.getStatus()).thenReturn(AiControlPlaneDrainStatusView.builder()
                .lastExecution(AiControlPlaneDrainExecutionSummary.builder()
                        .executedAt(123456789L)
                        .operator("ops-admin")
                        .dryRun(false)
                        .accepted(true)
                        .message("drain applied")
                        .build())
                .report(AiControlPlaneDrainReportSummary.builder()
                        .exists(true)
                        .path("/tmp/ai_control_plane_redis_drain.json")
                        .source("runtime-admin")
                        .executedAt(123456790L)
                        .operator("ops-admin")
                        .dryRun(false)
                        .accepted(true)
                        .batchSize(64)
                        .requestedStores(List.of("MYSQL_WRITE_OUTBOX", "CASE_MATERIALIZATION"))
                        .mysqlWriteOutboxRedisPending(3)
                        .mysqlWriteOutboxMysqlWritten(3)
                        .mysqlWriteOutboxRedisRemaining(0)
                        .caseMaterializationRedisPending(2)
                        .caseMaterializationMysqlWritten(2)
                        .caseMaterializationRedisRemaining(0)
                        .build())
                .build());
        when(drainStatusProvider.getRecentHistory(10)).thenReturn(List.of(
                AiPersistenceHistoryEntry.builder()
                        .reportType(AiControlPlaneDrainService.DRAIN_AUDIT_EVENT_TYPE)
                        .occurredAt(123456789L)
                        .reportPath("/tmp/history/ai_control_plane_redis_drain_123456789.json")
                        .operator("ops-admin")
                        .accepted(true)
                        .dryRun(false)
                        .message("drain applied")
                        .build()
        ));

        when(businessLiveFlowStatusProvider.getStatus()).thenReturn(AiBusinessLiveFlowStatusView.builder()
                .report(AiBusinessLiveFlowReportSummary.builder()
                        .exists(true)
                        .path("/tmp/ai_business_live_flow.json")
                        .verifiedAt(2233445566L)
                        .scene("OFFLINE_FLAP")
                        .success(true)
                        .deviceId("dev-live-proof-1")
                        .eventId("evt-live-proof-1")
                        .diagnosisId("diag-live-proof-1")
                        .feedbackId("feedback-live-proof-1")
                        .caseId("case-live-proof-1")
                        .mysqlWriteOutboxCount(0)
                        .caseMaterializationTaskCount(0)
                        .diagnosisMirrorExists(true)
                        .feedbackMirrorExists(true)
                        .caseMirrorExists(true)
                        .build())
                .build());
        when(businessLiveFlowStatusProvider.getRecentHistory(10)).thenReturn(List.of(
                AiPersistenceHistoryEntry.builder()
                        .reportType("BUSINESS_LIVE_FLOW")
                        .occurredAt(2233445566L)
                        .reportPath("/tmp/history/ai_business_live_flow_2233445566.json")
                        .operator("ops-admin")
                        .scene("OFFLINE_FLAP")
                        .success(true)
                        .message("case materialized successfully")
                        .build()
        ));

        AiPersistenceAdminQueryService service = new AiPersistenceAdminQueryService(
                statusService,
                drainStatusProvider,
                businessLiveFlowStatusProvider
        );

        AiPersistenceAdminQueryResp response = service.getQuery();

        assertTrue(Boolean.TRUE.equals(response.getStatus().getMysqlEnabled()));
        assertEquals("DUAL", response.getStatus().getReadMode());
        assertEquals("MYSQL_TABLE_PRIMARY", response.getStatus().getMysqlWriteOutboxStoreMode());
        assertTrue(Boolean.TRUE.equals(response.getControlPlaneDrain().getCompleted()));
        assertEquals(123456789L, response.getControlPlaneDrain().getLastDrainAt());
        assertEquals(64, response.getControlPlaneDrain().getReportBatchSize());
        assertEquals(1, response.getControlPlaneDrain().getRecentHistory().size());
        assertEquals("/tmp/history/ai_control_plane_redis_drain_123456789.json",
                response.getControlPlaneDrain().getRecentHistory().get(0).getReportPath());
        assertEquals("/tmp/ai_business_live_flow.json", response.getBusinessLiveFlow().getReportPath());
        assertEquals("OFFLINE_FLAP", response.getBusinessLiveFlow().getScene());
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlow().getSuccess()));
        assertEquals("case-live-proof-1", response.getBusinessLiveFlow().getCaseId());
        assertEquals(1, response.getBusinessLiveFlow().getRecentHistory().size());
        assertEquals("/tmp/history/ai_business_live_flow_2233445566.json",
                response.getBusinessLiveFlow().getRecentHistory().get(0).getReportPath());
    }
}
