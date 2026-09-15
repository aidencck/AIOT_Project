package com.aiot.rule.service;

import com.aiot.rule.config.AiPersistenceMysqlProperties;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
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
import java.util.List;
import java.util.Map;

@Service
public class AiPersistenceStatusService {

    private final AiPersistenceMysqlProperties aiPersistenceMysqlProperties;
    private final ObjectProvider<JdbcTemplate> jdbcTemplateProvider;
    private final ObjectMapper objectMapper;
    private final AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository;
    private final AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository;
    private final AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider;
    private final AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider;
    private final AiPersistenceReadModeResolver aiPersistenceReadModeResolver;
    private final String backfillManifestPath;
    private final String consistencyReportPath;
    private final String constraintsReportPath;
    private final String migrationGateReportPath;
    private final boolean mysqlWriteOutboxReplayEnabled;
    private final int mysqlWriteOutboxReplayBatchSize;
    private final long mysqlWriteOutboxReplayFixedDelayMs;
    private final boolean caseMaterializationReplayEnabled;
    private final int caseMaterializationReplayBatchSize;
    private final long caseMaterializationReplayFixedDelayMs;

    public AiPersistenceStatusService(AiPersistenceMysqlProperties aiPersistenceMysqlProperties,
                                      @Qualifier("aiMysqlJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
                                      ObjectMapper objectMapper,
                                      AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository,
                                      AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository,
                                      AiBusinessLiveFlowStatusProvider aiBusinessLiveFlowStatusProvider,
                                      AiControlPlaneDrainStatusProvider aiControlPlaneDrainStatusProvider,
                                      AiPersistenceReadModeResolver aiPersistenceReadModeResolver,
                                      @Value("${aiot.ai.persistence.mysql.backfill-manifest-path:training/data/backfill/redis_to_mysql_manifest.json}")
                                      String backfillManifestPath,
                                      @Value("${aiot.ai.persistence.mysql.consistency-report-path:training/data/reports/persistence/offline_flap_consistency.json}")
                                      String consistencyReportPath,
                                      @Value("${aiot.ai.persistence.mysql.constraints-report-path:training/data/reports/persistence/mysql_constraints_verification.json}")
                                      String constraintsReportPath,
                                      @Value("${aiot.ai.persistence.mysql.migration-gate-report-path:training/data/reports/persistence/offline_flap_migration_gate.json}")
                                      String migrationGateReportPath,
                                      @Value("${aiot.ai.persistence.mysql.outbox.replay-enabled:true}") boolean mysqlWriteOutboxReplayEnabled,
                                      @Value("${aiot.ai.persistence.mysql.outbox.batch-size:32}") int mysqlWriteOutboxReplayBatchSize,
                                      @Value("${aiot.ai.persistence.mysql.outbox.fixed-delay-ms:30000}") long mysqlWriteOutboxReplayFixedDelayMs,
                                      @Value("${aiot.ai.case-materialization.replay-enabled:true}") boolean caseMaterializationReplayEnabled,
                                      @Value("${aiot.ai.case-materialization.batch-size:16}") int caseMaterializationReplayBatchSize,
                                      @Value("${aiot.ai.case-materialization.fixed-delay-ms:30000}") long caseMaterializationReplayFixedDelayMs) {
        this.aiPersistenceMysqlProperties = aiPersistenceMysqlProperties;
        this.jdbcTemplateProvider = jdbcTemplateProvider;
        this.objectMapper = objectMapper;
        this.aiMysqlWriteOutboxRepository = aiMysqlWriteOutboxRepository;
        this.aiCaseMaterializationTaskRepository = aiCaseMaterializationTaskRepository;
        this.aiBusinessLiveFlowStatusProvider = aiBusinessLiveFlowStatusProvider;
        this.aiControlPlaneDrainStatusProvider = aiControlPlaneDrainStatusProvider;
        this.aiPersistenceReadModeResolver = aiPersistenceReadModeResolver;
        this.backfillManifestPath = backfillManifestPath;
        this.consistencyReportPath = consistencyReportPath;
        this.constraintsReportPath = constraintsReportPath;
        this.migrationGateReportPath = migrationGateReportPath;
        this.mysqlWriteOutboxReplayEnabled = mysqlWriteOutboxReplayEnabled;
        this.mysqlWriteOutboxReplayBatchSize = mysqlWriteOutboxReplayBatchSize;
        this.mysqlWriteOutboxReplayFixedDelayMs = mysqlWriteOutboxReplayFixedDelayMs;
        this.caseMaterializationReplayEnabled = caseMaterializationReplayEnabled;
        this.caseMaterializationReplayBatchSize = caseMaterializationReplayBatchSize;
        this.caseMaterializationReplayFixedDelayMs = caseMaterializationReplayFixedDelayMs;
    }

    public AiPersistenceStatusResponse getStatus() {
        Path manifestPath = resolveExistingPath(backfillManifestPath);
        Map<String, Object> manifest = readManifest(manifestPath);
        List<Map<String, Object>> sources = toSourceList(manifest.get("sources"));
        Path consistencyPath = resolveExistingPath(consistencyReportPath);
        Map<String, Object> consistency = readManifest(consistencyPath);
        Path constraintsPath = resolveExistingPath(constraintsReportPath);
        Map<String, Object> constraints = readManifest(constraintsPath);
        Path migrationGatePath = resolveExistingPath(migrationGateReportPath);
        Map<String, Object> migrationGate = readManifest(migrationGatePath);
        AiPersistenceReadDecision readDecision = aiPersistenceReadModeResolver.resolve();
        long outboxPendingCount = aiMysqlWriteOutboxRepository.countPending();
        long outboxLegacyRedisPendingCount = aiMysqlWriteOutboxRepository.countLegacyRedisPending();
        Long oldestFailedAt = aiMysqlWriteOutboxRepository.findOldestFailedAt();
        long casePendingCount = aiCaseMaterializationTaskRepository.countPending();
        long caseLegacyRedisPendingCount = aiCaseMaterializationTaskRepository.countLegacyRedisPending();
        Long oldestQueuedAt = aiCaseMaterializationTaskRepository.findOldestQueuedAt();
        String mysqlWriteOutboxStoreMode = aiMysqlWriteOutboxRepository.storageBackendMode();
        String caseMaterializationStoreMode = aiCaseMaterializationTaskRepository.storageBackendMode();
        var businessLiveFlowStatus = aiBusinessLiveFlowStatusProvider.getStatus();
        var businessLiveFlowReport = businessLiveFlowStatus == null ? null : businessLiveFlowStatus.getReport();
        var controlPlaneDrainStatus = aiControlPlaneDrainStatusProvider.getStatus();
        var lastDrain = controlPlaneDrainStatus == null ? null : controlPlaneDrainStatus.getLastExecution();
        boolean controlPlaneRedisDrainCompleted =
                "MYSQL_TABLE_PRIMARY".equals(mysqlWriteOutboxStoreMode)
                        && "MYSQL_TABLE_PRIMARY".equals(caseMaterializationStoreMode)
                        && outboxLegacyRedisPendingCount == 0
                        && caseLegacyRedisPendingCount == 0;
        return AiPersistenceStatusResponse.builder()
                .mysqlEnabled(aiPersistenceMysqlProperties.isEnabled())
                .readMode(readDecision.effectiveMode().name())
                .configuredReadMode(readDecision.configuredMode().name())
                .mysqlReady(jdbcTemplateProvider.getIfAvailable() != null)
                .mysqlCutoverReady(readDecision.mysqlCutoverReady())
                .mysqlCutoverBlockReason(readDecision.blockReason())
                .backfillManifestExists(Files.exists(manifestPath))
                .backfillManifestPath(manifestPath.toAbsolutePath().normalize().toString())
                .backfillSourceCount(sources.size())
                .backfillScannedTotal(sumSources(sources, "scanned"))
                .backfillWrittenTotal(sumSources(sources, "written"))
                .consistencyReportExists(Files.exists(consistencyPath))
                .consistencyPassed(readBoolean(consistency.get("consistent")))
                .consistencyTotalMismatch(readInteger(consistency.get("totalMismatchCount")))
                .constraintsReportExists(Files.exists(constraintsPath))
                .constraintsGatePassed(readBoolean(constraints.get("gatePassed")))
                .constraintsFailedCount(resolveConstraintsFailedCount(constraints))
                .migrationGateReportExists(Files.exists(migrationGatePath))
                .migrationGatePassed(readBoolean(migrationGate.get("gatePassed")))
                .mysqlWriteOutboxStoreMode(mysqlWriteOutboxStoreMode)
                .mysqlWriteOutboxLegacyRedisPendingCount(Math.toIntExact(outboxLegacyRedisPendingCount))
                .mysqlWriteOutboxReplayEnabled(mysqlWriteOutboxReplayEnabled)
                .mysqlWriteOutboxReplayBatchSize(mysqlWriteOutboxReplayBatchSize)
                .mysqlWriteOutboxReplayFixedDelayMs(mysqlWriteOutboxReplayFixedDelayMs)
                .mysqlWriteOutboxPendingCount(Math.toIntExact(outboxPendingCount))
                .mysqlWriteOutboxOldestAgeSeconds(resolveAgeSeconds(oldestFailedAt))
                .caseMaterializationStoreMode(caseMaterializationStoreMode)
                .caseMaterializationLegacyRedisPendingCount(Math.toIntExact(caseLegacyRedisPendingCount))
                .caseMaterializationReplayEnabled(caseMaterializationReplayEnabled)
                .caseMaterializationReplayBatchSize(caseMaterializationReplayBatchSize)
                .caseMaterializationReplayFixedDelayMs(caseMaterializationReplayFixedDelayMs)
                .caseMaterializationPendingCount(Math.toIntExact(casePendingCount))
                .caseMaterializationOldestAgeSeconds(resolveAgeSeconds(oldestQueuedAt))
                .controlPlaneRedisDrainCompleted(controlPlaneRedisDrainCompleted)
                .businessLiveFlowStatus(businessLiveFlowStatus)
                .businessLiveFlowReportExists(businessLiveFlowReport == null ? Boolean.FALSE : businessLiveFlowReport.getExists())
                .businessLiveFlowReportPath(businessLiveFlowReport == null ? null : businessLiveFlowReport.getPath())
                .businessLiveFlowVerifiedAt(businessLiveFlowReport == null ? null : businessLiveFlowReport.getVerifiedAt())
                .businessLiveFlowScene(businessLiveFlowReport == null ? null : businessLiveFlowReport.getScene())
                .businessLiveFlowSuccess(businessLiveFlowReport == null ? null : businessLiveFlowReport.getSuccess())
                .businessLiveFlowDeviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceId())
                .businessLiveFlowGlobalDeviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getGlobalDeviceId())
                .businessLiveFlowAuthIdentity(businessLiveFlowReport == null ? null : businessLiveFlowReport.getAuthIdentity())
                .businessLiveFlowDeviceSn(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceSn())
                .businessLiveFlowEventId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getEventId())
                .businessLiveFlowDiagnosisId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisId())
                .businessLiveFlowFeedbackId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackId())
                .businessLiveFlowCaseId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseId())
                .businessLiveFlowMysqlWriteOutboxCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getMysqlWriteOutboxCount())
                .businessLiveFlowCaseMaterializationTaskCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMaterializationTaskCount())
                .businessLiveFlowDiagnosisMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisMirrorExists())
                .businessLiveFlowFeedbackMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackMirrorExists())
                .businessLiveFlowCaseMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMirrorExists())
                .controlPlaneDrainStatus(controlPlaneDrainStatus)
                .controlPlaneLastDrainAt(lastDrain == null ? null : lastDrain.getExecutedAt())
                .controlPlaneLastDrainOperator(lastDrain == null ? null : lastDrain.getOperator())
                .controlPlaneLastDrainDryRun(lastDrain == null ? null : lastDrain.getDryRun())
                .controlPlaneLastDrainAccepted(lastDrain == null ? null : lastDrain.getAccepted())
                .controlPlaneLastDrainMessage(lastDrain == null ? null : lastDrain.getMessage())
                .build();
    }

    private Long resolveAgeSeconds(Long oldestFailedAt) {
        if (oldestFailedAt == null) {
            return 0L;
        }
        long ageMillis = Math.max(System.currentTimeMillis() - oldestFailedAt, 0L);
        return ageMillis / 1000L;
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

    private Map<String, Object> readManifest(Path manifestPath) {
        if (!Files.exists(manifestPath)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(manifestPath.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toSourceList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .toList();
        }
        return List.of();
    }

    private Integer resolveConstraintsFailedCount(Map<String, Object> constraints) {
        return countFailedChecks(constraints.get("constraintChecks")) + countFailedChecks(constraints.get("behaviorChecks"));
    }

    @SuppressWarnings("unchecked")
    private Integer countFailedChecks(Object value) {
        if (!(value instanceof List<?> list)) {
            return 0;
        }
        int failed = 0;
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && !readBoolean(((Map<String, Object>) map).get("passed"))) {
                failed++;
            }
        }
        return failed;
    }

    private Integer sumSources(List<Map<String, Object>> sources, String key) {
        int total = 0;
        for (Map<String, Object> source : sources) {
            total += readInteger(source.get(key));
        }
        return total;
    }

    private Integer readInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // Skip invalid manifest values.
            }
        }
        return 0;
    }

    private Boolean readBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return Boolean.FALSE;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
