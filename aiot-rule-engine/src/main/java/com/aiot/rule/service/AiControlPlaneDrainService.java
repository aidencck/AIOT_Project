package com.aiot.rule.service;

import com.aiot.rule.dto.AiControlPlaneDrainRequest;
import com.aiot.rule.dto.AiControlPlaneDrainResponse;
import com.aiot.rule.model.AiCaseMaterializationTask;
import com.aiot.rule.model.AiMysqlWriteOutboxTask;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.repository.AiCaseMaterializationTaskRepository;
import com.aiot.rule.repository.AiMysqlWriteOutboxRepository;
import com.aiot.rule.repository.OpsRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiControlPlaneDrainService {

    public static final String DRAIN_AUDIT_EVENT_TYPE = "AI_CONTROL_PLANE_REDIS_DRAIN";
    public static final String STORE_MYSQL_WRITE_OUTBOX = "MYSQL_WRITE_OUTBOX";
    public static final String STORE_CASE_MATERIALIZATION = "CASE_MATERIALIZATION";

    private final AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository;
    private final AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository;
    private final OpsRecordRepository opsRecordRepository;
    private final ObjectMapper objectMapper;
    private final int maxBatchSize;
    private final String reportPath;
    private final String historyIndexPath;
    private final int historyLimit;

    public AiControlPlaneDrainService(AiMysqlWriteOutboxRepository aiMysqlWriteOutboxRepository,
                                      AiCaseMaterializationTaskRepository aiCaseMaterializationTaskRepository,
                                      OpsRecordRepository opsRecordRepository,
                                      ObjectMapper objectMapper,
                                      @Value("${aiot.ai.persistence.control-plane-drain.max-batch-size:200}") int maxBatchSize,
                                      @Value("${aiot.ai.persistence.control-plane-drain.report-path:training/data/reports/persistence/ai_control_plane_redis_drain.json}")
                                      String reportPath,
                                      @Value("${aiot.ai.persistence.control-plane-drain.history-index-path:training/data/reports/persistence/ai_control_plane_redis_drain_index.json}")
                                      String historyIndexPath,
                                      @Value("${aiot.ai.persistence.control-plane-drain.history-limit:20}") int historyLimit) {
        this.aiMysqlWriteOutboxRepository = aiMysqlWriteOutboxRepository;
        this.aiCaseMaterializationTaskRepository = aiCaseMaterializationTaskRepository;
        this.opsRecordRepository = opsRecordRepository;
        this.objectMapper = objectMapper;
        this.maxBatchSize = Math.max(maxBatchSize, 1);
        this.reportPath = reportPath;
        this.historyIndexPath = historyIndexPath;
        this.historyLimit = Math.max(historyLimit, 1);
    }

    public AiControlPlaneDrainResponse drain(AiControlPlaneDrainRequest request) {
        boolean dryRun = request.getDryRun() == null || request.getDryRun();
        return drain(request.getOperator(), dryRun, request.getBatchSize(), request.getStores());
    }

    public AiControlPlaneDrainResponse drain(String operator, boolean dryRun) {
        return drain(operator, dryRun, null, null);
    }

    public AiControlPlaneDrainResponse drain(String operator, boolean dryRun, Integer requestedBatchSize, List<String> requestedStores) {
        long executedAt = System.currentTimeMillis();
        List<String> selectedStores = resolveRequestedStores(requestedStores);
        int effectiveBatchSize = resolveBatchSize(requestedBatchSize);
        String mysqlWriteOutboxStoreMode = aiMysqlWriteOutboxRepository.storageBackendMode();
        String caseMaterializationStoreMode = aiCaseMaterializationTaskRepository.storageBackendMode();
        int mysqlWriteOutboxBefore = Math.toIntExact(aiMysqlWriteOutboxRepository.countLegacyRedisPending());
        int caseMaterializationBefore = Math.toIntExact(aiCaseMaterializationTaskRepository.countLegacyRedisPending());
        boolean mysqlPrimary = !selectedStores.contains(STORE_MYSQL_WRITE_OUTBOX)
                || "MYSQL_TABLE_PRIMARY".equals(mysqlWriteOutboxStoreMode);
        boolean casePrimary = !selectedStores.contains(STORE_CASE_MATERIALIZATION)
                || "MYSQL_TABLE_PRIMARY".equals(caseMaterializationStoreMode);
        boolean accepted = dryRun || (mysqlPrimary && casePrimary);
        int mysqlWriteOutboxMigrated = 0;
        int caseMaterializationMigrated = 0;
        String message;
        if (!accepted) {
            message = "control-plane drain requires MYSQL_TABLE_PRIMARY for selected stores";
        } else if (dryRun) {
            message = "dry-run only, no legacy redis task migrated";
        } else {
            if (selectedStores.contains(STORE_MYSQL_WRITE_OUTBOX)) {
                mysqlWriteOutboxMigrated = migrateMysqlWriteOutbox(effectiveBatchSize);
            }
            if (selectedStores.contains(STORE_CASE_MATERIALIZATION)) {
                caseMaterializationMigrated = migrateCaseMaterializationTasks(effectiveBatchSize);
            }
            message = "legacy redis control-plane tasks migrated into mysql stores";
        }
        int mysqlWriteOutboxAfter = Math.toIntExact(aiMysqlWriteOutboxRepository.countLegacyRedisPending());
        int caseMaterializationAfter = Math.toIntExact(aiCaseMaterializationTaskRepository.countLegacyRedisPending());
        boolean completed = ("MYSQL_TABLE_PRIMARY".equals(mysqlWriteOutboxStoreMode) || !selectedStores.contains(STORE_MYSQL_WRITE_OUTBOX))
                && ("MYSQL_TABLE_PRIMARY".equals(caseMaterializationStoreMode) || !selectedStores.contains(STORE_CASE_MATERIALIZATION))
                && mysqlWriteOutboxAfter == 0
                && caseMaterializationAfter == 0;
        AiControlPlaneDrainResponse response = AiControlPlaneDrainResponse.builder()
                .accepted(accepted)
                .dryRun(dryRun)
                .batchSize(effectiveBatchSize)
                .requestedStores(selectedStores)
                .mysqlWriteOutboxStoreMode(mysqlWriteOutboxStoreMode)
                .caseMaterializationStoreMode(caseMaterializationStoreMode)
                .mysqlWriteOutboxLegacyRedisBefore(mysqlWriteOutboxBefore)
                .caseMaterializationLegacyRedisBefore(caseMaterializationBefore)
                .mysqlWriteOutboxMigrated(mysqlWriteOutboxMigrated)
                .caseMaterializationMigrated(caseMaterializationMigrated)
                .mysqlWriteOutboxLegacyRedisAfter(mysqlWriteOutboxAfter)
                .caseMaterializationLegacyRedisAfter(caseMaterializationAfter)
                .controlPlaneRedisDrainCompleted(completed)
                .reportPath(resolveReportPath().toAbsolutePath().normalize().toString())
                .message(message)
                .executedAt(executedAt)
                .operator(operator)
                .build();
        writeDrainReport(response);
        appendAudit(response);
        return response;
    }

    private int migrateMysqlWriteOutbox(int limit) {
        List<AiMysqlWriteOutboxTask> tasks = aiMysqlWriteOutboxRepository.listLegacyRedisPending(limit);
        for (AiMysqlWriteOutboxTask task : tasks) {
            aiMysqlWriteOutboxRepository.save(task);
        }
        return tasks.size();
    }

    private int migrateCaseMaterializationTasks(int limit) {
        List<AiCaseMaterializationTask> tasks = aiCaseMaterializationTaskRepository.listLegacyRedisPending(limit);
        for (AiCaseMaterializationTask task : tasks) {
            aiCaseMaterializationTaskRepository.save(task);
        }
        return tasks.size();
    }

    private List<String> resolveRequestedStores(List<String> requestedStores) {
        if (requestedStores == null || requestedStores.isEmpty()) {
            return List.of(STORE_MYSQL_WRITE_OUTBOX, STORE_CASE_MATERIALIZATION);
        }
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        for (String store : requestedStores) {
            if (!StringUtils.hasText(store)) {
                continue;
            }
            String normalized = store.trim().toUpperCase();
            if (STORE_MYSQL_WRITE_OUTBOX.equals(normalized) || STORE_CASE_MATERIALIZATION.equals(normalized)) {
                resolved.add(normalized);
            }
        }
        if (resolved.isEmpty()) {
            return List.of(STORE_MYSQL_WRITE_OUTBOX, STORE_CASE_MATERIALIZATION);
        }
        return List.copyOf(resolved);
    }

    private int resolveBatchSize(Integer requestedBatchSize) {
        if (requestedBatchSize == null || requestedBatchSize <= 0) {
            return maxBatchSize;
        }
        return Math.min(requestedBatchSize, maxBatchSize);
    }

    private void appendAudit(AiControlPlaneDrainResponse response) {
        AuditRecord audit = AuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .eventType(DRAIN_AUDIT_EVENT_TYPE)
                .operator(response.getOperator())
                .targetId("ai-control-plane")
                .details(serializeResponse(response))
                .traceId(MDC.get("traceId"))
                .createdAt(response.getExecutedAt())
                .build();
        opsRecordRepository.saveAudit(audit);
    }

    private String serializeResponse(AiControlPlaneDrainResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize control-plane drain response", e);
        }
    }

    private void writeDrainReport(AiControlPlaneDrainResponse response) {
        Path targetPath = resolveReportPath();
        Path archivePath = resolveArchiveReportPath(response.getExecutedAt());
        Path indexPath = resolveHistoryIndexPath();
        Map<String, Object> report = buildReport(response);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.createDirectories(archivePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetPath.toFile(), report);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(archivePath.toFile(), report);
            writeHistoryIndex(indexPath, buildHistoryEntry(response, archivePath));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write control-plane drain report", ex);
        }
    }

    private Path resolveReportPath() {
        Path path = Path.of(reportPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private Path resolveHistoryIndexPath() {
        Path path = Path.of(historyIndexPath);
        if (path.isAbsolute()) {
            return path;
        }
        return Path.of(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private Path resolveArchiveReportPath(Long executedAt) {
        Path report = resolveReportPath();
        String fileName = report.getFileName() == null ? "ai_control_plane_redis_drain.json" : report.getFileName().toString();
        int suffixIndex = fileName.lastIndexOf('.');
        String stem = suffixIndex >= 0 ? fileName.substring(0, suffixIndex) : fileName;
        String ext = suffixIndex >= 0 ? fileName.substring(suffixIndex) : ".json";
        return report.getParent().resolve("history").resolve(stem + "_" + executedAt + ext).normalize();
    }

    private Map<String, Object> buildReport(AiControlPlaneDrainResponse response) {
        List<Map<String, Object>> stores = new ArrayList<>();
        stores.add(Map.of(
                "name", "mysqlWriteOutbox",
                "storeMode", safeString(response.getMysqlWriteOutboxStoreMode()),
                "selected", response.getRequestedStores() != null && response.getRequestedStores().contains(STORE_MYSQL_WRITE_OUTBOX),
                "redisPending", valueOrZero(response.getMysqlWriteOutboxLegacyRedisBefore()),
                "mysqlWritten", valueOrZero(response.getMysqlWriteOutboxMigrated()),
                "redisDeleted", 0,
                "redisRemaining", valueOrZero(response.getMysqlWriteOutboxLegacyRedisAfter())
        ));
        stores.add(Map.of(
                "name", "caseMaterialization",
                "storeMode", safeString(response.getCaseMaterializationStoreMode()),
                "selected", response.getRequestedStores() != null && response.getRequestedStores().contains(STORE_CASE_MATERIALIZATION),
                "redisPending", valueOrZero(response.getCaseMaterializationLegacyRedisBefore()),
                "mysqlWritten", valueOrZero(response.getCaseMaterializationMigrated()),
                "redisDeleted", 0,
                "redisRemaining", valueOrZero(response.getCaseMaterializationLegacyRedisAfter())
        ));
        return Map.of(
                "source", "runtime-admin",
                "operator", response.getOperator(),
                "executedAt", response.getExecutedAt(),
                "dryRun", Boolean.TRUE.equals(response.getDryRun()),
                "accepted", Boolean.TRUE.equals(response.getAccepted()),
                "batchSize", valueOrZero(response.getBatchSize()),
                "requestedStores", response.getRequestedStores() == null ? List.of() : response.getRequestedStores(),
                "controlPlaneRedisDrainCompleted", Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()),
                "message", safeString(response.getMessage()),
                "stores", stores
        );
    }

    @SuppressWarnings("unchecked")
    private void writeHistoryIndex(Path indexPath, Map<String, Object> entry) throws Exception {
        List<Map<String, Object>> entries = new ArrayList<>();
        if (Files.exists(indexPath)) {
            Map<String, Object> payload = objectMapper.readValue(indexPath.toFile(), Map.class);
            Object rawEntries = payload.get("entries");
            if (rawEntries instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        entries.add((Map<String, Object>) map);
                    }
                }
            }
        }
        String reportPathValue = String.valueOf(entry.get("reportPath"));
        entries.removeIf(item -> reportPathValue.equals(String.valueOf(item.get("reportPath"))));
        entries.add(0, entry);
        List<Map<String, Object>> staleEntries = entries.size() > historyLimit
                ? new ArrayList<>(entries.subList(historyLimit, entries.size()))
                : List.of();
        if (entries.size() > historyLimit) {
            entries = new ArrayList<>(entries.subList(0, historyLimit));
        }
        Files.createDirectories(indexPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(indexPath.toFile(), Map.of(
                "reportType", DRAIN_AUDIT_EVENT_TYPE,
                "updatedAt", System.currentTimeMillis(),
                "entries", entries
        ));
        pruneStaleHistoryFiles(staleEntries);
    }

    private Map<String, Object> buildHistoryEntry(AiControlPlaneDrainResponse response, Path archivePath) {
        return Map.of(
                "reportType", DRAIN_AUDIT_EVENT_TYPE,
                "occurredAt", response.getExecutedAt(),
                "reportPath", archivePath.toAbsolutePath().normalize().toString(),
                "operator", safeString(response.getOperator()),
                "scene", "",
                "success", Boolean.TRUE.equals(response.getControlPlaneRedisDrainCompleted()),
                "accepted", Boolean.TRUE.equals(response.getAccepted()),
                "dryRun", Boolean.TRUE.equals(response.getDryRun()),
                "message", safeString(response.getMessage())
        );
    }

    private void pruneStaleHistoryFiles(List<Map<String, Object>> staleEntries) {
        for (Map<String, Object> staleEntry : staleEntries) {
            Object reportPathValue = staleEntry.get("reportPath");
            if (reportPathValue == null) {
                continue;
            }
            try {
                Path stalePath = Path.of(String.valueOf(reportPathValue));
                Files.deleteIfExists(stalePath);
            } catch (Exception ignored) {
                // Keep best-effort pruning non-blocking to avoid affecting drain execution.
            }
        }
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }
}
