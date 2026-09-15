package com.aiot.rule.service;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.config.AiPersistenceReadMode;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
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

class AiPersistenceReadModeResolverTest {

    @Test
    void shouldFallbackToRedisWhenMysqlCutoverEvidenceMissing() {
        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        properties.setReadMode("MYSQL");
        properties.setCutoverGuardCacheTtlMs(0L);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JdbcTemplate.class));
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(outboxRepository.countPending()).thenReturn(0L);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        when(caseTaskRepository.countPending()).thenReturn(0L);

        AiPersistenceReadModeResolver resolver = new AiPersistenceReadModeResolver(
                properties,
                provider,
                new ObjectMapper(),
                outboxRepository,
                caseTaskRepository,
                "missing/offline_flap_consistency.json",
                "missing/mysql_constraints_verification.json",
                "missing/offline_flap_migration_gate.json"
        );

        AiPersistenceReadDecision decision = resolver.resolve();

        assertEquals(AiPersistenceReadMode.MYSQL, decision.configuredMode());
        assertEquals(AiPersistenceReadMode.REDIS, decision.effectiveMode());
        assertFalse(decision.mysqlCutoverReady());
        assertEquals("consistency report missing", decision.blockReason());
    }

    @Test
    void shouldAllowMysqlWhenEvidenceAndQueuesAreGreen() throws Exception {
        Path tempDir = Files.createTempDirectory("ai-persistence-cutover");
        Path consistencyPath = tempDir.resolve("offline_flap_consistency.json");
        Path constraintsPath = tempDir.resolve("mysql_constraints_verification.json");
        Path migrationGatePath = tempDir.resolve("offline_flap_migration_gate.json");
        Files.writeString(consistencyPath, "{\"consistent\":true}");
        Files.writeString(constraintsPath, "{\"gatePassed\":true}");
        Files.writeString(migrationGatePath, "{\"gatePassed\":true}");

        AiPersistenceMysqlProperties properties = new AiPersistenceMysqlProperties();
        properties.setEnabled(true);
        properties.setReadMode("MYSQL");
        properties.setCutoverGuardCacheTtlMs(0L);
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mock(JdbcTemplate.class));
        AiMysqlWriteOutboxRepository outboxRepository = mock(AiMysqlWriteOutboxRepository.class);
        when(outboxRepository.countPending()).thenReturn(0L);
        AiCaseMaterializationTaskRepository caseTaskRepository = mock(AiCaseMaterializationTaskRepository.class);
        when(caseTaskRepository.countPending()).thenReturn(0L);

        AiPersistenceReadModeResolver resolver = new AiPersistenceReadModeResolver(
                properties,
                provider,
                new ObjectMapper(),
                outboxRepository,
                caseTaskRepository,
                consistencyPath.toString(),
                constraintsPath.toString(),
                migrationGatePath.toString()
        );

        AiPersistenceReadDecision decision = resolver.resolve();

        assertEquals(AiPersistenceReadMode.MYSQL, decision.configuredMode());
        assertEquals(AiPersistenceReadMode.MYSQL, decision.effectiveMode());
        assertTrue(decision.mysqlCutoverReady());
        assertEquals(null, decision.blockReason());
    }
}
