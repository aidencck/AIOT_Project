package com.aiot.rule.service;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.config.AiPersistenceReadMode;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class AiPersistenceReadModeResolver {

    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;
    private final AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository;
    private final AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository;
    private final String consistencyReportPath;
    private final String constraintsReportPath;
    private final String migrationGateReportPath;

    private volatile AiPersistenceReadDecision cachedDecision;
    private volatile long cachedAtMillis;

    public AiPersistenceReadModeResolver(
            AiPersistenceMysqlProperties aiPersistenceMysqlProperties,
            @Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectMapper objectMapper,
            AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository,
            AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository,
            @Value("${aiot.ai.persistence.mysql.consistency-report-path:training/data/reports/persistence/offline_flap_consistency.json}")
            String consistencyReportPath,
            @Value("${aiot.ai.persistence.mysql.constraints-report-path:training/data/reports/persistence/mysql_constraints_verification.json}")
            String constraintsReportPath,
            @Value("${aiot.ai.persistence.mysql.migration-gate-report-path:training/data/reports/persistence/offline_flap_migration_gate.json}")
            String migrationGateReportPath
    ) {
        this.aiPersistenceMysqlProperties = aiPersistenceMysqlProperties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapper;
        this.aiMysqlWriteOutboxRepository = aiMysqlWriteOutboxRepository;
        this.aiCaseMaterializationTaskRepository = aiCaseMaterializationTaskRepository;
        this.consistencyReportPath = consistencyReportPath;
        this.constraintsReportPath = constraintsReportPath;
        this.migrationGateReportPath = migrationGateReportPath;
    }

    public AiPersistenceReadDecision resolve() {
        long now = System.currentTimeMillis();
        AiPersistenceReadDecision current = cachedDecision;
        if (current != null && now - cachedAtMillis <= Math.max(aiPersistenceMysqlProperties.getCutoverGuardCacheTtlMs(), 0L)) {
            return current;
        }
        synchronized (this) {
            current = cachedDecision;
            now = System.currentTimeMillis();
            if (current != null && now - cachedAtMillis <= Math.max(aiPersistenceMysqlProperties.getCutoverGuardCacheTtlMs(), 0L)) {
                return current;
            }
            AiPersistenceReadDecision refreshed = evaluateDecision();
            cachedDecision = refreshed;
            cachedAtMillis = now;
            return refreshed;
        }
    }

    private AiPersistenceReadDecision evaluateDecision() {
        AiPersistenceReadMode configuredMode = aiPersistenceMysqlProperties.resolvedReadMode();
        String blockReason = resolveMysqlCutoverBlockReason();
        boolean mysqlCutoverReady = blockReason == null;
        AiPersistenceReadMode effectiveMode = configuredMode;
        if (configuredMode == AiPersistenceReadMode.MYSQL
                && aiPersistenceMysqlProperties.isCutoverGuardEnabled()
                && !aiPersistenceMysqlProperties.isAllowUnsafeMysqlRead()
                && !mysqlCutoverReady) {
            effectiveMode = AiPersistenceReadMode.REDIS;
        }
        return new AiPersistenceReadDecision(configuredMode, effectiveMode, mysqlCutoverReady, blockReason);
    }

    private String resolveMysqlCutoverBlockReason() {
        if (!aiPersistenceMysqlProperties.isEnabled()) {
            return "mysql persistence disabled";
        }
        if (jdbcTemplateProvider.getIfAvailable() == null) {
            return "mysql jdbc template unavailable";
        }
        Path consistencyPath = resolveExistingPath(consistencyReportPath);
        if (!Files.exists(consistencyPath)) {
            return "consistency report missing";
        }
        Map<String, Object> consistency = readReport(consistencyPath);
        if (!readBoolean(consistency.get("consistent"))) {
            return "consistency report not passed";
        }
        Path constraintsPath = resolveExistingPath(constraintsReportPath);
        if (!Files.exists(constraintsPath)) {
            return "constraints report missing";
        }
        Map<String, Object> constraints = readReport(constraintsPath);
        if (!readBoolean(constraints.get("gatePassed"))) {
            return "constraints report not passed";
        }
        Path migrationGatePath = resolveExistingPath(migrationGateReportPath);
        if (!Files.exists(migrationGatePath)) {
            return "migration gate report missing";
        }
        Map<String, Object> migrationGate = readReport(migrationGatePath);
        if (!readBoolean(migrationGate.get("gatePassed"))) {
            return "migration gate not passed";
        }
        long mysqlOutboxPending = aiMysqlWriteOutboxRepository.countPending();
        if (mysqlOutboxPending > 0) {
            return "mysql write outbox pending=" + mysqlOutboxPending;
        }
        long casePending = aiCaseMaterializationTaskRepository.countPending();
        if (casePending > 0) {
            return "case materialization pending=" + casePending;
        }
        return null;
    }

    private Path resolveExistingPath(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path;
        }
        Path current = Path.of(System.getProperty("user.dir"));
        for (int i = 0; i < 4; i++) {
            Path candidate = current.resolve(configuredPath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            if (current.getParent() == null) {
                break;
            }
            current = current.getParent();
        }
        return Path.of(System.getProperty("user.dir")).resolve(configuredPath);
    }

    private Map<String, Object> readReport(Path reportPath) {
        if (!Files.exists(reportPath)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(reportPath.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
