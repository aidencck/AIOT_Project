package com.aiot.rule.service;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import com.aiot.rule.repository.OpsRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPersistenceStatusServiceTest {

    @Test
    void shouldReadBackfillManifestAndExposeMysqlReadiness() throws Exception {
        Path manifestDir = Files.createTempDirectory("ai-persistence-status");
        Path manifestPath = manifestDir.resolve("redis_to_mysql_manifest.json");
        Path consistencyPath = manifestDir.resolve("offline_flap_consistency.json");
        Path constraintsPath = manifestDir.resolve("mysql_constraints_verification.json");
        Path migrationGatePath = manifestDir.resolve("offline_flap_migration_gate.json");
        Path businessLiveFlowPath = manifestDir.resolve("ai_business_live_flow.json");
        Path drainReportPath = manifestDir.resolve("ai_control_plane_redis_drain.json");
        Files.writeString(
                manifestPath,
                """
                        {
                          "sources": [
                            { "name": "diagnosis", "scanned": 10, "written": 9 },
                            { "name": "feedback", "scanned": 8, "written": 8 }
                          ]
                        }
                        """
        );
        Files.writeString(
                consistencyPath,
                """
                        {
                          "consistent": true,
                          "totalMismatchCount": 0
                        }
                        """
        );
        Files.writeString(
                constraintsPath,
                """
                        {
                          "gatePassed": true,
                          "constraintChecks": [
                            { "passed": true }
                          ],
                          "behaviorChecks": [
                            { "passed": true }
                          ]
                        }
                        """
        );
        Files.writeString(
                migrationGatePath,
                """
                        {
                          "gatePassed": true
                        }
                        """
        );
        Files.writeString(
                businessLiveFlowPath,
                """
                        {
                          "verifiedAt": 2233445566,
                          "scene": "OFFLINE_FLAP",
                          "success": true,
                          "requests": {
                            "diagnosis": {
                              "deviceId": "dev-live-proof-1",
                              "eventId": "evt-live-proof-1"
                            }
                          },
                          "responses": {
                            "diagnosis": {
                              "diagnosisId": "diag-live-proof-1"
                            },
                            "feedback": {
                              "feedbackId": "feedback-live-proof-1",
                              "caseId": "case-live-proof-1"
                            }
                          },
                          "mysql": {
                            "mysqlWriteOutboxCount": 0,
                            "caseMaterializationTaskCount": 0
                          },
                          "redis": {
                            "diagnosisMirrorExists": true,
                            "feedbackMirrorExists": true,
                            "caseMirrorExists": true
                          }
                        }
                        """
        );
        Files.writeString(
                drainReportPath,
                """
                        {
                          "source": "runtime-admin",
                          "operator": "ops-admin",
                          "executedAt": 123456789,
                          "dryRun": false,
                          "accepted": true,
                          "batchSize": 64,
                          "requestedStores": ["MYSQL_WRITE_OUTBOX", "CASE_MATERIALIZATION"],
                          "controlPlaneRedisDrainCompleted": true,
                          "message": "legacy redis control-plane tasks migrated into mysql stores",
                          "stores": [
                            {
                              "name": "mysqlWriteOutbox",
                              "redisPending": 3,
                              "mysqlWritten": 3,
                              "redisRemaining": 0
                            },
                            {
                              "name": "caseMaterialization",
                              "redisPending": 2,
                              "mysqlWritten": 2,
                              "redisRemaining": 0
                            }
                          ]
                        }
                        """
        );
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        properties.setReadMode("DUAL");
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JdbcTemplate.class));
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(outboxRepository.countPending()).thenReturn(2L);
        when(outboxRepository.countLegacyRedisPending()).thenReturn(0L);
        when(outboxRepository.findOldestFailedAt()).thenReturn(System.currentTimeMillis() - 15_000L);
        when(outboxRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        when(caseTaskRepository.countPending()).thenReturn(1L);
        when(caseTaskRepository.countLegacyRedisPending()).thenReturn(0L);
        when(caseTaskRepository.findOldestQueuedAt()).thenReturn(System.currentTimeMillis() - 9_000L);
        when(caseTaskRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(opsRecordRepository.findAllAudits()).thenReturn(java.util.List.of(
                AuditRecord.builder()
                        .auditId("audit-1")
                        .eventType(AiControlPlaneDrainService.DRAIN_AUDIT_EVENT_TYPE)
                        .operator("ops-admin")
                        .details("""
                                {
                                  "accepted": true,
                                  "dryRun": false,
                                  "message": "legacy redis control-plane tasks migrated into mysql stores"
                                }
                                """)
                        .createdAt(123456789L)
                        .build()
        ));
        AiPersistenceReadModeResolver readModeResolver = mock(AiPersistenceReadModeResolver.class);
        when(readModeResolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));
        AiBusinessLiveFlowStatusProvider businessLiveFlowStatusProvider = new AiBusinessLiveFlowStatusProvider(
                new ObjectMapper(),
                businessLiveFlowPath.toString(),
                manifestDir.resolve("ai_business_live_flow_index.json").toString()
        );
        AiControlPlaneDrainStatusProvider drainStatusProvider = new AiControlPlaneDrainStatusProvider(
                new ObjectMapper(),
                opsRecordRepository,
                drainReportPath.toString(),
                manifestDir.resolve("ai_control_plane_redis_drain_index.json").toString()
        );

        AiPersistenceStatusService service = new AiPersistenceStatusService(
                properties,
                provider,
                new ObjectMapper(),
                outboxRepository,
                caseTaskRepository,
                businessLiveFlowStatusProvider,
                drainStatusProvider,
                readModeResolver,
                manifestPath.toString(),
                consistencyPath.toString(),
                constraintsPath.toString(),
                migrationGatePath.toString(),
                true,
                32,
                30000L,
                true,
                16,
                30000L
        );

        AiPersistenceStatusResponse response = service.getStatus();

        assertTrue(Boolean.TRUE.equals(response.getMysqlEnabled()));
        assertEquals("DUAL", response.getReadMode());
        assertEquals("DUAL", response.getConfiguredReadMode());
        assertTrue(Boolean.TRUE.equals(response.getMysqlReady()));
        assertTrue(Boolean.TRUE.equals(response.getMysqlCutoverReady()));
        assertEquals(null, response.getMysqlCutoverBlockReason());
        assertTrue(Boolean.TRUE.equals(response.getBackfillManifestExists()));
        assertEquals(2, response.getBackfillSourceCount());
        assertEquals(18, response.getBackfillScannedTotal());
        assertEquals(17, response.getBackfillWrittenTotal());
        assertTrue(Boolean.TRUE.equals(response.getConsistencyReportExists()));
        assertTrue(Boolean.TRUE.equals(response.getConsistencyPassed()));
        assertEquals(0, response.getConsistencyTotalMismatch());
        assertTrue(Boolean.TRUE.equals(response.getConstraintsReportExists()));
        assertTrue(Boolean.TRUE.equals(response.getConstraintsGatePassed()));
        assertEquals(0, response.getConstraintsFailedCount());
        assertTrue(Boolean.TRUE.equals(response.getMigrationGateReportExists()));
        assertTrue(Boolean.TRUE.equals(response.getMigrationGatePassed()));
        assertEquals("MYSQL_TABLE_PRIMARY", response.getMysqlWriteOutboxStoreMode());
        assertEquals(0, response.getMysqlWriteOutboxLegacyRedisPendingCount());
        assertTrue(Boolean.TRUE.equals(response.getMysqlWriteOutboxReplayEnabled()));
        assertEquals(32, response.getMysqlWriteOutboxReplayBatchSize());
        assertEquals(30000L, response.getMysqlWriteOutboxReplayFixedDelayMs());
        assertEquals(2, response.getMysqlWriteOutboxPendingCount());
        assertTrue(response.getMysqlWriteOutboxOldestAgeSeconds() >= 10);
        assertEquals("MYSQL_TABLE_PRIMARY", response.getCaseMaterializationStoreMode());
        assertEquals(0, response.getCaseMaterializationLegacyRedisPendingCount());
        assertTrue(Boolean.TRUE.equals(response.getCaseMaterializationReplayEnabled()));
        assertEquals(16, response.getCaseMaterializationReplayBatchSize());
        assertEquals(30000L, response.getCaseMaterializationReplayFixedDelayMs());
        assertEquals(1, response.getCaseMaterializationPendingCount());
        assertTrue(response.getCaseMaterializationOldestAgeSeconds() >= 5);
        assertTrue(Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()));
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlowReportExists()));
        assertEquals("OFFLINE_FLAP", response.getBusinessLiveFlowScene());
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlowSuccess()));
        assertEquals("dev-live-proof-1", response.getBusinessLiveFlowDeviceId());
        assertEquals("dev-live-proof-1", response.getBusinessLiveFlowGlobalDeviceId());
        assertEquals(null, response.getBusinessLiveFlowAuthIdentity());
        assertEquals(null, response.getBusinessLiveFlowDeviceSn());
        assertEquals("evt-live-proof-1", response.getBusinessLiveFlowEventId());
        assertEquals("diag-live-proof-1", response.getBusinessLiveFlowDiagnosisId());
        assertEquals("feedback-live-proof-1", response.getBusinessLiveFlowFeedbackId());
        assertEquals("case-live-proof-1", response.getBusinessLiveFlowCaseId());
        assertEquals(0, response.getBusinessLiveFlowMysqlWriteOutboxCount());
        assertEquals(0, response.getBusinessLiveFlowCaseMaterializationTaskCount());
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlowDiagnosisMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlowFeedbackMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getBusinessLiveFlowCaseMirrorExists()));
        assertTrue(Boolean.TRUE.equals(response.getControlPlaneDrainStatus().getReport().getExists()));
        assertEquals("runtime-admin", response.getControlPlaneDrainStatus().getReport().getSource());
        assertEquals(64, response.getControlPlaneDrainStatus().getReport().getBatchSize());
        assertEquals(3, response.getControlPlaneDrainStatus().getReport().getMysqlWriteOutboxMysqlWritten());
        assertEquals(2, response.getControlPlaneDrainStatus().getReport().getCaseMaterializationMysqlWritten());
        assertEquals(123456789L, response.getControlPlaneLastDrainAt());
        assertEquals("ops-admin", response.getControlPlaneLastDrainOperator());
        assertFalse(Boolean.TRUE.equals(response.getControlPlaneLastDrainDryRun()));
        assertTrue(Boolean.TRUE.equals(response.getControlPlaneLastDrainAccepted()));
        assertEquals("legacy redis control-plane tasks migrated into mysql stores", response.getControlPlaneLastDrainMessage());
    }

    @Test
    void shouldReturnEmptyBackfillSummaryWhenManifestMissing() {
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(false);
        properties.setReadMode("REDIS");
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(outboxRepository.countPending()).thenReturn(0L);
        when(outboxRepository.countLegacyRedisPending()).thenReturn(2L);
        when(outboxRepository.findOldestFailedAt()).thenReturn(null);
        when(outboxRepository.storageBackendMode()).thenReturn("REDIS_HASH_ONLY");
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        when(caseTaskRepository.countPending()).thenReturn(0L);
        when(caseTaskRepository.countLegacyRedisPending()).thenReturn(1L);
        when(caseTaskRepository.findOldestQueuedAt()).thenReturn(null);
        when(caseTaskRepository.storageBackendMode()).thenReturn("REDIS_HASH_ONLY");
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(opsRecordRepository.findAllAudits()).thenReturn(java.util.List.of());
        AiPersistenceReadModeResolver readModeResolver = mock(AiPersistenceReadModeResolver.class);
        when(readModeResolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));
        AiBusinessLiveFlowStatusProvider businessLiveFlowStatusProvider = new AiBusinessLiveFlowStatusProvider(
                new ObjectMapper(),
                "missing/ai_business_live_flow.json",
                "missing/ai_business_live_flow_index.json"
        );
        AiControlPlaneDrainStatusProvider drainStatusProvider = new AiControlPlaneDrainStatusProvider(
                new ObjectMapper(),
                opsRecordRepository,
                "missing/ai_control_plane_redis_drain.json",
                "missing/ai_control_plane_redis_drain_index.json"
        );

        AiPersistenceStatusService service = new AiPersistenceStatusService(
                properties,
                provider,
                new ObjectMapper(),
                outboxRepository,
                caseTaskRepository,
                businessLiveFlowStatusProvider,
                drainStatusProvider,
                readModeResolver,
                "missing/redis_to_mysql_manifest.json",
                "missing/offline_flap_consistency.json",
                "missing/mysql_constraints_verification.json",
                "missing/offline_flap_migration_gate.json",
                false,
                8,
                45000L,
                false,
                4,
                60000L
        );

        AiPersistenceStatusResponse response = service.getStatus();

        assertFalse(Boolean.TRUE.equals(response.getMysqlEnabled()));
        assertEquals("REDIS", response.getReadMode());
        assertEquals("REDIS", response.getConfiguredReadMode());
        assertFalse(Boolean.TRUE.equals(response.getMysqlReady()));
        assertTrue(Boolean.TRUE.equals(response.getMysqlCutoverReady()));
        assertEquals(null, response.getMysqlCutoverBlockReason());
        assertFalse(Boolean.TRUE.equals(response.getBackfillManifestExists()));
        assertEquals(0, response.getBackfillSourceCount());
        assertEquals(0, response.getBackfillScannedTotal());
        assertEquals(0, response.getBackfillWrittenTotal());
        assertFalse(Boolean.TRUE.equals(response.getConsistencyReportExists()));
        assertFalse(Boolean.TRUE.equals(response.getConsistencyPassed()));
        assertEquals(0, response.getConsistencyTotalMismatch());
        assertFalse(Boolean.TRUE.equals(response.getConstraintsReportExists()));
        assertFalse(Boolean.TRUE.equals(response.getConstraintsGatePassed()));
        assertEquals(0, response.getConstraintsFailedCount());
        assertFalse(Boolean.TRUE.equals(response.getMigrationGateReportExists()));
        assertFalse(Boolean.TRUE.equals(response.getMigrationGatePassed()));
        assertEquals("REDIS_HASH_ONLY", response.getMysqlWriteOutboxStoreMode());
        assertEquals(2, response.getMysqlWriteOutboxLegacyRedisPendingCount());
        assertFalse(Boolean.TRUE.equals(response.getMysqlWriteOutboxReplayEnabled()));
        assertEquals(8, response.getMysqlWriteOutboxReplayBatchSize());
        assertEquals(45000L, response.getMysqlWriteOutboxReplayFixedDelayMs());
        assertEquals(0, response.getMysqlWriteOutboxPendingCount());
        assertEquals(0L, response.getMysqlWriteOutboxOldestAgeSeconds());
        assertEquals("REDIS_HASH_ONLY", response.getCaseMaterializationStoreMode());
        assertEquals(1, response.getCaseMaterializationLegacyRedisPendingCount());
        assertFalse(Boolean.TRUE.equals(response.getCaseMaterializationReplayEnabled()));
        assertEquals(4, response.getCaseMaterializationReplayBatchSize());
        assertEquals(60000L, response.getCaseMaterializationReplayFixedDelayMs());
        assertEquals(0, response.getCaseMaterializationPendingCount());
        assertEquals(0L, response.getCaseMaterializationOldestAgeSeconds());
        assertFalse(Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()));
        assertFalse(Boolean.TRUE.equals(response.getBusinessLiveFlowReportExists()));
        assertEquals(null, response.getBusinessLiveFlowVerifiedAt());
        assertFalse(Boolean.TRUE.equals(response.getControlPlaneDrainStatus().getReport().getExists()));
        assertEquals(java.util.List.of(), response.getControlPlaneDrainStatus().getReport().getRequestedStores());
        assertEquals(null, response.getControlPlaneLastDrainAt());
        assertEquals(null, response.getControlPlaneLastDrainOperator());
    }

    @Test
    void shouldTolerateSparseDrainReportSummary() throws Exception {
        Path reportDir = Files.createTempDirectory("ai-persistence-drain-report");
        Path drainReportPath = reportDir.resolve("ai_control_plane_redis_drain.json");
        Files.writeString(
                drainReportPath,
                """
                        {
                          "source": "training-script",
                          "stores": [
                            {
                              "name": "mysqlWriteOutbox",
                              "redisPending": 5
                            }
                          ]
                        }
                        """
        );
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        properties.setReadMode("MYSQL");
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JdbcTemplate.class));
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(outboxRepository.countPending()).thenReturn(0L);
        when(outboxRepository.countLegacyRedisPending()).thenReturn(0L);
        when(outboxRepository.findOldestFailedAt()).thenReturn(null);
        when(outboxRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        when(caseTaskRepository.countPending()).thenReturn(0L);
        when(caseTaskRepository.countLegacyRedisPending()).thenReturn(0L);
        when(caseTaskRepository.findOldestQueuedAt()).thenReturn(null);
        when(caseTaskRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(opsRecordRepository.findAllAudits()).thenReturn(java.util.List.of());
        AiPersistenceReadModeResolver readModeResolver = mock(AiPersistenceReadModeResolver.class);
        when(readModeResolver.resolve()).thenReturn(new AiPersistenceReadDecision(
                properties.resolvedReadMode(),
                properties.resolvedReadMode(),
                true,
                null
        ));
        AiBusinessLiveFlowStatusProvider businessLiveFlowStatusProvider = new AiBusinessLiveFlowStatusProvider(
                new ObjectMapper(),
                "missing/ai_business_live_flow.json",
                "missing/ai_business_live_flow_index.json"
        );
        AiControlPlaneDrainStatusProvider drainStatusProvider = new AiControlPlaneDrainStatusProvider(
                new ObjectMapper(),
                opsRecordRepository,
                drainReportPath.toString(),
                reportDir.resolve("ai_control_plane_redis_drain_index.json").toString()
        );

        AiPersistenceStatusService service = new AiPersistenceStatusService(
                properties,
                provider,
                new ObjectMapper(),
                outboxRepository,
                caseTaskRepository,
                businessLiveFlowStatusProvider,
                drainStatusProvider,
                readModeResolver,
                "missing/redis_to_mysql_manifest.json",
                "missing/offline_flap_consistency.json",
                "missing/mysql_constraints_verification.json",
                "missing/offline_flap_migration_gate.json",
                true,
                8,
                30000L,
                true,
                8,
                30000L
        );

        AiPersistenceStatusResponse response = service.getStatus();

        assertTrue(Boolean.TRUE.equals(response.getControlPlaneDrainStatus().getReport().getExists()));
        assertEquals("training-script", response.getControlPlaneDrainStatus().getReport().getSource());
        assertEquals(5, response.getControlPlaneDrainStatus().getReport().getMysqlWriteOutboxRedisPending());
        assertEquals(null, response.getControlPlaneDrainStatus().getReport().getBatchSize());
        assertEquals(java.util.List.of(), response.getControlPlaneDrainStatus().getReport().getRequestedStores());
        assertEquals(null, response.getControlPlaneLastDrainAt());
    }
}
