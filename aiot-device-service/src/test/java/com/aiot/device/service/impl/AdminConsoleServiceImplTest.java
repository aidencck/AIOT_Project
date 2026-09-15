package com.aiot.device.service.impl;

import com.aiot.common.dto.ai.AiBusinessLiveFlowSnapshot;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.AdminAiEvalReportResp;
import com.aiot.device.dto.AdminWorkbenchResp;
import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.dto.ProductResp;
import com.aiot.device.service.AdminConsoleRemoteQueryFacade;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.OtaService;
import com.aiot.device.service.ProductService;
import com.aiot.device.service.AiPersistenceAdminFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminConsoleServiceImplTest {

    private ProductService productService;
    private DeviceService deviceService;
    private OtaService otaService;
    private AiPersistenceAdminFacade aiPersistenceAdminFacade;
    private AdminConsoleRemoteQueryFacade adminConsoleRemoteQueryFacade;
    private AdminConsoleServiceImpl adminConsoleService;

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        deviceService = mock(DeviceService.class);
        otaService = mock(OtaService.class);
        aiPersistenceAdminFacade = mock(AiPersistenceAdminFacade.class);
        adminConsoleRemoteQueryFacade = mock(AdminConsoleRemoteQueryFacade.class);
        adminConsoleService = new AdminConsoleServiceImpl(
                productService,
                deviceService,
                otaService,
                aiPersistenceAdminFacade,
                adminConsoleRemoteQueryFacade
        );
    }

    @Test
    void shouldAggregateAiEvalReportsForOverview() {
        when(adminConsoleRemoteQueryFacade.loadHomes("Bearer token"))
                .thenReturn(List.of(Map.of("id", "home-1")));
        when(adminConsoleRemoteQueryFacade.loadHomeMembers("home-1", "Bearer token"))
                .thenReturn(List.of(Map.of("id", "member-1")));
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("todayAlarmCount", 2);
        overview.put("pendingWorkOrderCount", 1);
        overview.put("oneTimeResolveRate", 0.75D);
        overview.put("aiPersistenceMysqlEnabled", true);
        overview.put("aiPersistenceReadMode", "REDIS");
        overview.put("aiPersistenceConfiguredReadMode", "MYSQL");
        overview.put("aiPersistenceMysqlReady", true);
        overview.put("aiPersistenceMysqlCutoverReady", false);
        overview.put("aiPersistenceMysqlCutoverBlockReason", "migration gate not passed");
        overview.put("aiPersistenceBackfillManifestExists", true);
        overview.put("aiPersistenceBackfillWrittenTotal", 17);
        overview.put("aiPersistenceConsistencyReportExists", true);
        overview.put("aiPersistenceConsistencyPassed", true);
        overview.put("aiPersistenceConsistencyTotalMismatch", 0);
        overview.put("aiPersistenceConstraintsReportExists", true);
        overview.put("aiPersistenceConstraintsGatePassed", true);
        overview.put("aiPersistenceConstraintsFailedCount", 0);
        overview.put("aiPersistenceMigrationGatePassed", true);
        overview.put("aiPersistenceMysqlWriteOutboxStoreMode", "MYSQL_TABLE_PRIMARY");
        overview.put("aiPersistenceMysqlWriteOutboxLegacyRedisPendingCount", 0);
        overview.put("aiPersistenceMysqlWriteOutboxReplayEnabled", true);
        overview.put("aiPersistenceMysqlWriteOutboxReplayBatchSize", 32);
        overview.put("aiPersistenceMysqlWriteOutboxReplayFixedDelayMs", 30000L);
        overview.put("aiPersistenceMysqlWriteOutboxPendingCount", 3);
        overview.put("aiPersistenceMysqlWriteOutboxOldestAgeSeconds", 45L);
        overview.put("aiPersistenceCaseMaterializationStoreMode", "MYSQL_TABLE_PRIMARY");
        overview.put("aiPersistenceCaseMaterializationLegacyRedisPendingCount", 0);
        overview.put("aiPersistenceCaseMaterializationReplayEnabled", true);
        overview.put("aiPersistenceCaseMaterializationReplayBatchSize", 16);
        overview.put("aiPersistenceCaseMaterializationReplayFixedDelayMs", 30000L);
        overview.put("aiCaseMaterializationPendingCount", 2);
        overview.put("aiCaseMaterializationOldestAgeSeconds", 90L);
        overview.put("aiPersistenceControlPlaneRedisDrainCompleted", true);
        overview.put("aiPersistenceBusinessLiveFlowReportExists", true);
        overview.put("aiPersistenceBusinessLiveFlowReportPath", "/tmp/ai_business_live_flow.json");
        overview.put("aiPersistenceBusinessLiveFlowVerifiedAt", 123456788L);
        overview.put("aiPersistenceBusinessLiveFlowScene", "OFFLINE_FLAP");
        overview.put("aiPersistenceBusinessLiveFlowSuccess", true);
        overview.put("aiPersistenceBusinessLiveFlowDeviceId", "dev-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowGlobalDeviceId", "gdev-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowAuthIdentity", "auth-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowDeviceSn", "sn-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowEventId", "evt-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowDiagnosisId", "diag-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowFeedbackId", "feedback-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowCaseId", "case-live-proof-1");
        overview.put("aiPersistenceBusinessLiveFlowMysqlWriteOutboxCount", 0);
        overview.put("aiPersistenceBusinessLiveFlowCaseMaterializationTaskCount", 0);
        overview.put("aiPersistenceBusinessLiveFlowDiagnosisMirrorExists", true);
        overview.put("aiPersistenceBusinessLiveFlowFeedbackMirrorExists", true);
        overview.put("aiPersistenceBusinessLiveFlowCaseMirrorExists", true);
        overview.put("aiPersistenceControlPlaneLastDrainAt", 123456789L);
        overview.put("aiPersistenceControlPlaneLastDrainOperator", "ops-admin");
        overview.put("aiPersistenceControlPlaneLastDrainDryRun", false);
        overview.put("aiPersistenceControlPlaneLastDrainAccepted", true);
        overview.put("aiPersistenceControlPlaneLastDrainMessage", "legacy redis control-plane tasks migrated into mysql stores");
        overview.put("aiPersistenceControlPlaneDrainReportExists", true);
        overview.put("aiPersistenceControlPlaneDrainReportPath", "/tmp/ai_control_plane_redis_drain.json");
        overview.put("aiPersistenceControlPlaneDrainReportSource", "runtime-admin");
        overview.put("aiPersistenceControlPlaneDrainReportExecutedAt", 123456790L);
        overview.put("aiPersistenceControlPlaneDrainReportOperator", "ops-admin");
        overview.put("aiPersistenceControlPlaneDrainReportDryRun", false);
        overview.put("aiPersistenceControlPlaneDrainReportAccepted", true);
        overview.put("aiPersistenceControlPlaneDrainReportBatchSize", 64);
        overview.put("aiPersistenceControlPlaneDrainReportRequestedStores", "MYSQL_WRITE_OUTBOX,CASE_MATERIALIZATION");
        overview.put("aiPersistenceControlPlaneDrainReportMessage", "legacy redis control-plane tasks migrated into mysql stores");
        overview.put("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending", 3);
        overview.put("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten", 3);
        overview.put("aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining", 0);
        overview.put("aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending", 2);
        overview.put("aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten", 2);
        overview.put("aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining", 0);
        when(adminConsoleRemoteQueryFacade.loadOpsOverview()).thenReturn(overview);
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("OFFLINE_FLAP"))
                .thenReturn(evalReport("OFFLINE_FLAP", true, true, 1000L, 10, 1.0D));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("PROVISION_FAILURE"))
                .thenReturn(evalReport("PROVISION_FAILURE", true, false, 2000L, 8, 0.88D));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("SHADOW_DIFF"))
                .thenReturn(evalReport("SHADOW_DIFF", false, false, null, 0, 0D));
        when(productService.listProducts()).thenReturn(List.of());
        when(deviceService.listDevicesByHomeId("home-1")).thenReturn(List.of());
        when(otaService.listUpgradeTasks("home-1")).thenReturn(List.of());

        AdminConsoleOverviewResp response = adminConsoleService.getOverview("home-1", "Bearer token");

        assertEquals(2, response.getAiEvalReadyCount());
        assertEquals(1, response.getAiEvalPassedCount());
        assertEquals(2000L, response.getAiEvalLastGeneratedAt());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceMysqlEnabled()));
        assertEquals("REDIS", response.getAiPersistenceReadMode());
        assertEquals("MYSQL", response.getAiPersistenceConfiguredReadMode());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceMysqlReady()));
        assertFalse(Boolean.TRUE.equals(response.getAiPersistenceMysqlCutoverReady()));
        assertEquals("migration gate not passed", response.getAiPersistenceMysqlCutoverBlockReason());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBackfillManifestExists()));
        assertEquals(17, response.getAiPersistenceBackfillWrittenTotal());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceConsistencyReportExists()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceConsistencyPassed()));
        assertEquals(0, response.getAiPersistenceConsistencyTotalMismatch());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceConstraintsReportExists()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceConstraintsGatePassed()));
        assertEquals(0, response.getAiPersistenceConstraintsFailedCount());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceMigrationGatePassed()));
        assertEquals("MYSQL_TABLE_PRIMARY", response.getAiPersistenceMysqlWriteOutboxStoreMode());
        assertEquals(0, response.getAiPersistenceMysqlWriteOutboxLegacyRedisPendingCount());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceMysqlWriteOutboxReplayEnabled()));
        assertEquals(32, response.getAiPersistenceMysqlWriteOutboxReplayBatchSize());
        assertEquals(30000L, response.getAiPersistenceMysqlWriteOutboxReplayFixedDelayMs());
        assertEquals(3, response.getAiPersistenceMysqlWriteOutboxPendingCount());
        assertEquals(45L, response.getAiPersistenceMysqlWriteOutboxOldestAgeSeconds());
        assertEquals("MYSQL_TABLE_PRIMARY", response.getAiPersistenceCaseMaterializationStoreMode());
        assertEquals(0, response.getAiPersistenceCaseMaterializationLegacyRedisPendingCount());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceCaseMaterializationReplayEnabled()));
        assertEquals(16, response.getAiPersistenceCaseMaterializationReplayBatchSize());
        assertEquals(30000L, response.getAiPersistenceCaseMaterializationReplayFixedDelayMs());
        assertEquals(2, response.getAiCaseMaterializationPendingCount());
        assertEquals(90L, response.getAiCaseMaterializationOldestAgeSeconds());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneRedisDrainCompleted()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBusinessLiveFlowReportExists()));
        assertEquals("/tmp/ai_business_live_flow.json", response.getAiPersistenceBusinessLiveFlowReportPath());
        assertEquals(123456788L, response.getAiPersistenceBusinessLiveFlowVerifiedAt());
        assertEquals("OFFLINE_FLAP", response.getAiPersistenceBusinessLiveFlowScene());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBusinessLiveFlowSuccess()));
        assertEquals("dev-live-proof-1", response.getAiPersistenceBusinessLiveFlowDeviceId());
        assertEquals("gdev-live-proof-1", response.getAiPersistenceBusinessLiveFlowGlobalDeviceId());
        assertEquals("auth-live-proof-1", response.getAiPersistenceBusinessLiveFlowAuthIdentity());
        assertEquals("sn-live-proof-1", response.getAiPersistenceBusinessLiveFlowDeviceSn());
        assertEquals("evt-live-proof-1", response.getAiPersistenceBusinessLiveFlowEventId());
        assertEquals("diag-live-proof-1", response.getAiPersistenceBusinessLiveFlowDiagnosisId());
        assertEquals("feedback-live-proof-1", response.getAiPersistenceBusinessLiveFlowFeedbackId());
        assertEquals("case-live-proof-1", response.getAiPersistenceBusinessLiveFlowCaseId());
        assertEquals(0, response.getAiPersistenceBusinessLiveFlowMysqlWriteOutboxCount());
        assertEquals(0, response.getAiPersistenceBusinessLiveFlowCaseMaterializationTaskCount());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBusinessLiveFlowDiagnosisMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBusinessLiveFlowFeedbackMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceBusinessLiveFlowCaseMirrorExists()));
        assertEquals(123456789L, response.getAiPersistenceControlPlaneLastDrainAt());
        assertEquals("ops-admin", response.getAiPersistenceControlPlaneLastDrainOperator());
        assertFalse(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneLastDrainDryRun()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneLastDrainAccepted()));
        assertEquals("legacy redis control-plane tasks migrated into mysql stores", response.getAiPersistenceControlPlaneLastDrainMessage());
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneDrainReportExists()));
        assertEquals("/tmp/ai_control_plane_redis_drain.json", response.getAiPersistenceControlPlaneDrainReportPath());
        assertEquals("runtime-admin", response.getAiPersistenceControlPlaneDrainReportSource());
        assertEquals(123456790L, response.getAiPersistenceControlPlaneDrainReportExecutedAt());
        assertEquals("ops-admin", response.getAiPersistenceControlPlaneDrainReportOperator());
        assertFalse(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneDrainReportDryRun()));
        assertTrue(Boolean.TRUE.equals(response.getAiPersistenceControlPlaneDrainReportAccepted()));
        assertEquals(64, response.getAiPersistenceControlPlaneDrainReportBatchSize());
        assertEquals("MYSQL_WRITE_OUTBOX,CASE_MATERIALIZATION", response.getAiPersistenceControlPlaneDrainReportRequestedStores());
        assertEquals("legacy redis control-plane tasks migrated into mysql stores", response.getAiPersistenceControlPlaneDrainReportMessage());
        assertEquals(3, response.getAiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending());
        assertEquals(3, response.getAiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten());
        assertEquals(0, response.getAiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining());
        assertEquals(2, response.getAiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending());
        assertEquals(2, response.getAiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten());
        assertEquals(0, response.getAiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining());
    }

    @Test
    void shouldNormalizeCommaSeparatedSceneTypesForAiEvalReports() {
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("OFFLINE_FLAP"))
                .thenReturn(evalReport("OFFLINE_FLAP", true, true, 1000L, 5, 1.0D));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("SHADOW_DIFF"))
                .thenReturn(evalReport("SHADOW_DIFF", false, false, null, 0, 0D));

        List<AdminAiEvalReportResp> responses = adminConsoleService.listAiEvalReports(
                List.of("offline_flap,shadow_diff"),
                "Bearer token"
        );

        assertEquals(2, responses.size());
        assertEquals("OFFLINE_FLAP", responses.get(0).getSceneType());
        assertTrue(Boolean.TRUE.equals(responses.get(0).getGatePassed()));
        assertEquals("SHADOW_DIFF", responses.get(1).getSceneType());
        assertFalse(Boolean.TRUE.equals(responses.get(1).getExists()));
    }

    @Test
    void shouldBuildWorkbenchClosureStages() {
        when(adminConsoleRemoteQueryFacade.loadHomes("Bearer token"))
                .thenReturn(List.of(Map.of("id", "home-1", "name", "上海总部样板间")));
        when(adminConsoleRemoteQueryFacade.loadHomeMembers("home-1", "Bearer token"))
                .thenReturn(List.of(Map.of("id", "member-1")));
        when(productService.listProducts()).thenReturn(List.of(mock(ProductResp.class)));
        when(deviceService.listDevicesByHomeId("home-1")).thenReturn(List.of(mock(DeviceResp.class)));
        when(otaService.listUpgradeTasks("home-1")).thenReturn(List.of());
        when(adminConsoleRemoteQueryFacade.loadOpsOverview()).thenReturn(Map.of(
                "todayAlarmCount", 3,
                "pendingWorkOrderCount", 1,
                "oneTimeResolveRate", 0.8D,
                "aiPersistenceReadMode", "REDIS",
                "aiPersistenceConfiguredReadMode", "DUAL",
                "aiPersistenceMysqlCutoverReady", false,
                "aiPersistenceMysqlCutoverBlockReason", "consistency gate not passed"
        ));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("OFFLINE_FLAP"))
                .thenReturn(evalReport("OFFLINE_FLAP", true, true, 1000L, 5, 1.0D));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("PROVISION_FAILURE"))
                .thenReturn(evalReport("PROVISION_FAILURE", true, false, 900L, 5, 0.8D));
        when(adminConsoleRemoteQueryFacade.loadAiEvalRegressionGate("SHADOW_DIFF"))
                .thenReturn(evalReport("SHADOW_DIFF", false, false, null, 0, 0D));

        AdminWorkbenchResp response = adminConsoleService.getWorkbench("home-1", "Bearer token");

        assertEquals("home-1", response.getSelectedHomeId());
        assertEquals(86, response.getClosureScore());
        assertEquals(8, response.getClosureStages().size());
        assertEquals("READY", response.getClosureStages().get(0).getStatus());
        assertEquals("OPTIONAL", response.getClosureStages().get(4).getStatus());
        assertEquals("ATTENTION", response.getClosureStages().get(7).getStatus());
        assertEquals("consistency gate not passed", response.getClosureStages().get(7).getActionHint());
        assertEquals(1, response.getOverview().getProductCount());
    }

    @Test
    void shouldMapAiBusinessLiveFlowFromIndependentEndpoint() {
        when(aiPersistenceAdminFacade.getBusinessLiveFlow()).thenReturn(AiBusinessLiveFlowSnapshot.builder()
                .reportExists(true)
                .reportPath("/tmp/ai_business_live_flow.json")
                .verifiedAt(123456789L)
                .scene("OFFLINE_FLAP")
                .success(true)
                .deviceId("dev-live-proof-1")
                .globalDeviceId("gdev-live-proof-1")
                .authIdentity("auth-live-proof-1")
                .deviceSn("sn-live-proof-1")
                .eventId("evt-live-proof-1")
                .diagnosisId("diag-live-proof-1")
                .feedbackId("feedback-live-proof-1")
                .caseId("case-live-proof-1")
                .mysqlWriteOutboxCount(0)
                .caseMaterializationTaskCount(0)
                .diagnosisMirrorExists(true)
                .feedbackMirrorExists(true)
                .caseMirrorExists(true)
                .build());

        var response = adminConsoleService.getAiBusinessLiveFlow("Bearer token");

        assertTrue(Boolean.TRUE.equals(response.getReportExists()));
        assertEquals("/tmp/ai_business_live_flow.json", response.getReportPath());
        assertEquals(123456789L, response.getVerifiedAt());
        assertEquals("OFFLINE_FLAP", response.getScene());
        assertTrue(Boolean.TRUE.equals(response.getSuccess()));
        assertEquals("dev-live-proof-1", response.getDeviceId());
        assertEquals("gdev-live-proof-1", response.getGlobalDeviceId());
        assertEquals("auth-live-proof-1", response.getAuthIdentity());
        assertEquals("sn-live-proof-1", response.getDeviceSn());
        assertEquals("evt-live-proof-1", response.getEventId());
        assertEquals("diag-live-proof-1", response.getDiagnosisId());
        assertEquals("feedback-live-proof-1", response.getFeedbackId());
        assertEquals("case-live-proof-1", response.getCaseId());
        assertEquals(0, response.getMysqlWriteOutboxCount());
        assertEquals(0, response.getCaseMaterializationTaskCount());
        assertTrue(Boolean.TRUE.equals(response.getDiagnosisMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getFeedbackMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getCaseMirrorExists()));
    }

    @Test
    void shouldReturnUnifiedDevicePageResponse() {
        when(adminConsoleRemoteQueryFacade.loadHomes("Bearer token"))
                .thenReturn(List.of(Map.of("id", "home-1")));

        DeviceResp deviceResp = new DeviceResp();
        deviceResp.setId("dev-1");
        DevicePageResp pageResp = new DevicePageResp();
        pageResp.setTotal(3L);
        pageResp.setPageNo(1);
        pageResp.setPageSize(20);
        pageResp.setRecords(List.of(deviceResp));
        when(deviceService.pageDevices(org.mockito.ArgumentMatchers.any())).thenReturn(pageResp);

        AdminDevicePageReq req = new AdminDevicePageReq();
        req.setPageNo(1);
        req.setPageSize(20);

        DevicePageResp response = adminConsoleService.pageDevices(req, "Bearer token");

        assertEquals(3L, response.getTotal());
        assertEquals("dev-1", response.getRecords().get(0).getId());
    }

    @Test
    void shouldReturnUnifiedOtaTaskPageResponse() {
        when(adminConsoleRemoteQueryFacade.loadHomes("Bearer token"))
                .thenReturn(List.of(Map.of("id", "home-1")));

        OtaUpgradeTaskResp taskResp = new OtaUpgradeTaskResp();
        taskResp.setTaskId("task-1");
        OtaTaskPageResp pageResp = new OtaTaskPageResp();
        pageResp.setTotal(5L);
        pageResp.setPageNo(2);
        pageResp.setPageSize(10);
        pageResp.setRecords(List.of(taskResp));
        when(otaService.pageUpgradeTasks(org.mockito.ArgumentMatchers.any())).thenReturn(pageResp);

        AdminOtaTaskPageReq req = new AdminOtaTaskPageReq();
        req.setPageNo(2);
        req.setPageSize(10);

        OtaTaskPageResp response = adminConsoleService.pageOtaTasks(req, "Bearer token");

        assertEquals(5L, response.getTotal());
        assertEquals("task-1", response.getRecords().get(0).getTaskId());
    }

    @Test
    void shouldReturnAiPersistenceQueryFromFacade() {
        AiPersistenceAdminQueryResp query = AiPersistenceAdminQueryResp.builder()
                .status(com.aiot.common.dto.ai.AiPersistenceStatusSnapshot.builder()
                        .mysqlEnabled(true)
                        .readMode("DUAL")
                        .build())
                .businessLiveFlow(AiBusinessLiveFlowSnapshot.builder()
                        .scene("OFFLINE_FLAP")
                        .success(true)
                        .build())
                .build();
        when(aiPersistenceAdminFacade.query()).thenReturn(query);

        AiPersistenceAdminQueryResp response = adminConsoleService.getAiPersistenceQuery("Bearer token");

        assertTrue(Boolean.TRUE.equals(response.getStatus().getMysqlEnabled()));
        assertEquals("DUAL", response.getStatus().getReadMode());
        assertEquals("OFFLINE_FLAP", response.getBusinessLiveFlow().getScene());
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlow().getSuccess()));
    }

    @Test
    void shouldReturnAiPersistenceHistoryDetailFromFacade() {
        when(aiPersistenceAdminFacade.getHistoryDetail("BUSINESS_LIVE_FLOW", 2233445566L))
                .thenReturn(AiPersistenceHistoryDetailResp.builder()
                        .reportType("BUSINESS_LIVE_FLOW")
                        .occurredAt(2233445566L)
                        .reportPath("/tmp/history/ai_business_live_flow_2233445566.json")
                        .exists(true)
                        .scene("OFFLINE_FLAP")
                        .success(true)
                        .content(Map.of("scene", "OFFLINE_FLAP"))
                        .build());

        AiPersistenceHistoryDetailResp response = adminConsoleService.getAiPersistenceHistoryDetail(
                "BUSINESS_LIVE_FLOW",
                2233445566L,
                "Bearer token"
        );

        assertTrue(Boolean.TRUE.equals(response.getExists()));
        assertEquals("/tmp/history/ai_business_live_flow_2233445566.json", response.getReportPath());
        assertEquals("OFFLINE_FLAP", response.getScene());
    }

    @Test
    void shouldRejectBlankHistoryReportType() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminConsoleService.getAiPersistenceHistoryDetail(" ", 2233445566L, "Bearer token"));

        assertEquals("reportType 不能为空", ex.getMessage());
    }

    @Test
    void shouldRejectNonPositiveHistoryOccurredAt() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminConsoleService.getAiPersistenceHistoryDetail("BUSINESS_LIVE_FLOW", 0L, "Bearer token"));

        assertEquals("occurredAt 必须为正整数", ex.getMessage());
    }

    private AdminAiEvalReportResp evalReport(String sceneType,
                                             boolean exists,
                                             boolean gatePassed,
                                             Long generatedAt,
                                             int total,
                                             double passRate) {
        return AdminAiEvalReportResp.builder()
                .sceneType(sceneType)
                .reportType("regression_gate")
                .reportPath("/tmp/" + sceneType.toLowerCase() + ".json")
                .exists(exists)
                .gatePassed(gatePassed)
                .generatedAt(generatedAt)
                .content(Map.of(
                        "gatePassed", gatePassed,
                        "total", total,
                        "schema_pass_rate", passRate
                ))
                .build();
    }
}
