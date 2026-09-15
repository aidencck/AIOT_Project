package com.aiot.rule.service;

import com.aiot.rule.dto.AiControlPlaneDrainResponse;
import com.aiot.rule.dto.AiControlPlaneDrainRequest;
import com.aiot.rule.model.AiCaseMaterializationTask;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import com.aiot.rule.repository.OpsRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiControlPlaneDrainServiceTest {

    @Test
    void shouldDryRunWithoutMutatingRepositories() {
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiCaseMaterializationTaskRepository caseRepository = mock(AiCaseMaterializationTaskRepository.class);
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(outboxRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        when(caseRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        when(outboxRepository.countLegacyRedisPending()).thenReturn(2L, 2L);
        when(caseRepository.countLegacyRedisPending()).thenReturn(1L, 1L);
        AiControlPlaneDrainService service = new AiControlPlaneDrainService(
                outboxRepository,
                caseRepository,
                opsRecordRepository,
                new ObjectMapper(),
                200,
                "target/test-drain-dry-run.json",
                "target/test-drain-dry-run-index.json",
                20
        );

        AiControlPlaneDrainResponse response = service.drain("ops-a", true);

        assertTrue(Boolean.TRUE.equals(response.getAccepted()));
        assertTrue(Boolean.TRUE.equals(response.getDryRun()));
        assertEquals(2, response.getMysqlWriteOutboxLegacyRedisBefore());
        assertEquals(1, response.getCaseMaterializationLegacyRedisBefore());
        assertEquals(0, response.getMysqlWriteOutboxMigrated());
        assertEquals(0, response.getCaseMaterializationMigrated());
        assertFalse(Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()));
        verify(opsRecordRepository).saveAudit(any());
    }

    @Test
    void shouldMigrateLegacyRedisTasksWhenMysqlStoresReady() throws Exception {
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiCaseMaterializationTaskRepository caseRepository = mock(AiCaseMaterializationTaskRepository.class);
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(outboxRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        when(caseRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        when(outboxRepository.countLegacyRedisPending()).thenReturn(1L, 0L);
        when(caseRepository.countLegacyRedisPending()).thenReturn(2L, 2L);
        when(outboxRepository.listLegacyRedisPending(2)).thenReturn(List.of(
                AiMysqlWriteOutboxTask.builder().taskId("outbox-1").entityType("diagnosis").recordKey("diag-1").payloadJson("{}").build()
        ));
        Path reportPath = Files.createTempDirectory("drain-report").resolve("ai_control_plane_redis_drain.json");
        AiControlPlaneDrainService service = new AiControlPlaneDrainService(
                outboxRepository,
                caseRepository,
                opsRecordRepository,
                new ObjectMapper(),
                2,
                reportPath.toString(),
                reportPath.resolveSibling("ai_control_plane_redis_drain_index.json").toString(),
                20
        );
        AiControlPlaneDrainRequest request = new AiControlPlaneDrainRequest();
        request.setOperator("ops-b");
        request.setDryRun(false);
        request.setBatchSize(5);
        request.setStores(List.of(AiControlPlaneDrainService.STORE_MYSQL_WRITE_OUTBOX));

        AiControlPlaneDrainResponse response = service.drain(request);

        assertTrue(Boolean.TRUE.equals(response.getAccepted()));
        assertFalse(Boolean.TRUE.equals(response.getDryRun()));
        assertEquals(2, response.getBatchSize());
        assertEquals(List.of(AiControlPlaneDrainService.STORE_MYSQL_WRITE_OUTBOX), response.getRequestedStores());
        assertEquals(1, response.getMysqlWriteOutboxMigrated());
        assertEquals(0, response.getCaseMaterializationMigrated());
        assertEquals(0, response.getMysqlWriteOutboxLegacyRedisAfter());
        assertEquals(2, response.getCaseMaterializationLegacyRedisAfter());
        assertFalse(Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()));
        assertNotNull(response.getReportPath());
        assertTrue(Files.exists(reportPath));
        verify(outboxRepository).save(any(AiMysqlWriteOutboxTask.class));
        verify(caseRepository, never()).save(any(AiCaseMaterializationTask.class));
        verify(opsRecordRepository).saveAudit(any());
    }

    @Test
    void shouldRejectApplyWhenMysqlStoresNotReady() {
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        AiCaseMaterializationTaskRepository caseRepository = mock(AiCaseMaterializationTaskRepository.class);
        OpsRecordRepository opsRecordRepository = mock(OpsRecordRepository.class);
        when(outboxRepository.storageBackendMode()).thenReturn("REDIS_HASH_ONLY");
        when(caseRepository.storageBackendMode()).thenReturn("MYSQL_TABLE_PRIMARY");
        when(outboxRepository.countLegacyRedisPending()).thenReturn(3L, 3L);
        when(caseRepository.countLegacyRedisPending()).thenReturn(0L, 0L);
        AiControlPlaneDrainService service = new AiControlPlaneDrainService(
                outboxRepository,
                caseRepository,
                opsRecordRepository,
                new ObjectMapper(),
                200,
                "target/test-drain-reject.json",
                "target/test-drain-reject-index.json",
                20
        );

        AiControlPlaneDrainResponse response = service.drain("ops-c", false);

        assertFalse(Boolean.TRUE.equals(response.getAccepted()));
        assertEquals("control-plane drain requires MYSQL_TABLE_PRIMARY for selected stores", response.getMessage());
        assertEquals(0, response.getMysqlWriteOutboxMigrated());
        assertEquals(0, response.getCaseMaterializationMigrated());
        verify(opsRecordRepository).saveAudit(any());
    }
}
