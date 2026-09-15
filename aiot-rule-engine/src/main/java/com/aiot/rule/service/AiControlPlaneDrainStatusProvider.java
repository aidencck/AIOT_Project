package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryEntry;
import com.aiot.rule.dto.AiControlPlaneDrainExecutionSummary;
import com.aiot.rule.dto.AiControlPlaneDrainReportSummary;
import com.aiot.rule.dto.AiControlPlaneDrainStatusView;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.repository.OpsRecordRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class AiControlPlaneDrainStatusProvider {

    private static final String DRAIN_AUDIT_EVENT_TYPE = "AI_CONTROL_PLANE_REDIS_DRAIN";

    private final ObjectMapper objectMapper;
    private final OpsRecordRepository opsRecordRepository;
    private final String reportPath;
    private final String historyIndexPath;

    public AiControlPlaneDrainStatusProvider(ObjectMapper objectMapper,
                                             OpsRecordRepository opsRecordRepository,
                                             @Value("${aiot.ai.persistence.control-plane-drain.report-path:training/data/reports/persistence/ai_control_plane_redis_drain.json}")
                                             String reportPath,
                                             @Value("${aiot.ai.persistence.control-plane-drain.history-index-path:training/data/reports/persistence/ai_control_plane_redis_drain_index.json}")
                                             String historyIndexPath) {
        this.objectMapper = objectMapper;
        this.opsRecordRepository = opsRecordRepository;
        this.reportPath = reportPath;
        this.historyIndexPath = historyIndexPath;
    }

    public AiControlPlaneDrainStatusView getStatus() {
        Path resolvedReportPath = resolvePath(reportPath);
        return AiControlPlaneDrainStatusView.builder()
                .lastExecution(resolveLastExecution())
                .report(resolveReportSummary(resolvedReportPath))
                .build();
    }

    public List<AiPersistenceHistoryEntry> getRecentHistory(int limit) {
        List<AiPersistenceHistoryEntry> history = readHistoryEntries(resolvePath(historyIndexPath), limit);
        if (!history.isEmpty()) {
            return history;
        }
        AiControlPlaneDrainStatusView status = getStatus();
        AiControlPlaneDrainReportSummary report = status == null ? null : status.getReport();
        AiControlPlaneDrainExecutionSummary lastExecution = status == null ? null : status.getLastExecution();
        if (report == null || !Boolean.TRUE.equals(report.getExists())) {
            return List.of();
        }
        return List.of(AiPersistenceHistoryEntry.builder()
                .reportType(DRAIN_AUDIT_EVENT_TYPE)
                .occurredAt(lastExecution == null ? report.getExecutedAt() : lastExecution.getExecutedAt())
                .reportPath(report.getPath())
                .operator(lastExecution == null ? report.getOperator() : lastExecution.getOperator())
                .accepted(lastExecution == null ? report.getAccepted() : lastExecution.getAccepted())
                .dryRun(lastExecution == null ? report.getDryRun() : lastExecution.getDryRun())
                .message(lastExecution == null ? report.getMessage() : lastExecution.getMessage())
                .build());
    }

    public AiPersistenceHistoryDetailResp getHistoryDetail(Long occurredAt) {
        AiPersistenceHistoryEntry entry = findHistoryEntry(occurredAt);
        if (entry == null || entry.getReportPath() == null) {
            return AiPersistenceHistoryDetailResp.builder()
                    .reportType(DRAIN_AUDIT_EVENT_TYPE)
                    .occurredAt(occurredAt)
                    .exists(Boolean.FALSE)
                    .content(Map.of())
                    .build();
        }
        Path detailPath = resolvePath(entry.getReportPath());
        return AiPersistenceHistoryDetailResp.builder()
                .reportType(entry.getReportType())
                .occurredAt(entry.getOccurredAt())
                .reportPath(detailPath.toAbsolutePath().normalize().toString())
                .exists(Files.exists(detailPath))
                .operator(entry.getOperator())
                .scene(entry.getScene())
                .success(entry.getSuccess())
                .accepted(entry.getAccepted())
                .dryRun(entry.getDryRun())
                .message(entry.getMessage())
                .content(readJsonFile(detailPath))
                .build();
    }

    private AiControlPlaneDrainExecutionSummary resolveLastExecution() {
        return opsRecordRepository.findAllAudits().stream()
                .filter(record -> DRAIN_AUDIT_EVENT_TYPE.equals(record.getEventType()))
                .filter(record -> StringUtils.hasText(record.getDetails()))
                .sorted((left, right) -> Long.compare(
                        right.getCreatedAt() == null ? 0L : right.getCreatedAt(),
                        left.getCreatedAt() == null ? 0L : left.getCreatedAt()))
                .findFirst()
                .map(this::parseLastExecution)
                .orElse(null);
    }

    private AiControlPlaneDrainExecutionSummary parseLastExecution(AuditRecord auditRecord) {
        Map<String, Object> payload = readJsonObject(auditRecord.getDetails());
        if (payload.isEmpty()) {
            return AiControlPlaneDrainExecutionSummary.builder()
                    .executedAt(auditRecord.getCreatedAt())
                    .operator(auditRecord.getOperator())
                    .dryRun(Boolean.FALSE)
                    .accepted(Boolean.FALSE)
                    .message("failed to parse last drain audit details")
                    .build();
        }
        return AiControlPlaneDrainExecutionSummary.builder()
                .executedAt(auditRecord.getCreatedAt())
                .operator(auditRecord.getOperator())
                .dryRun(readNullableBoolean(payload.get("dryRun")))
                .accepted(readNullableBoolean(payload.get("accepted")))
                .message(readNullableString(payload.get("message")))
                .build();
    }

    private AiControlPlaneDrainReportSummary resolveReportSummary(Path resolvedReportPath) {
        if (!Files.exists(resolvedReportPath)) {
            return AiControlPlaneDrainReportSummary.builder()
                    .exists(Boolean.FALSE)
                    .path(resolvedReportPath.toAbsolutePath().normalize().toString())
                    .requestedStores(List.of())
                    .build();
        }
        Map<String, Object> payload = readJsonFile(resolvedReportPath);
        Map<String, Object> mysqlWriteOutbox = findStore(payload.get("stores"), "mysqlWriteOutbox");
        Map<String, Object> caseMaterialization = findStore(payload.get("stores"), "caseMaterialization");
        return AiControlPlaneDrainReportSummary.builder()
                .exists(Boolean.TRUE)
                .path(resolvedReportPath.toAbsolutePath().normalize().toString())
                .source(readNullableString(payload.get("source")))
                .executedAt(readNullableLong(payload.get("executedAt")))
                .operator(readNullableString(payload.get("operator")))
                .dryRun(readNullableBoolean(payload.get("dryRun")))
                .accepted(readNullableBoolean(payload.get("accepted")))
                .batchSize(readNullableInteger(payload.get("batchSize")))
                .requestedStores(toStringList(payload.get("requestedStores")))
                .controlPlaneRedisDrainCompleted(readNullableBoolean(payload.get("controlPlaneRedisDrainCompleted")))
                .message(readNullableString(payload.get("message")))
                .mysqlWriteOutboxRedisPending(readNullableInteger(mysqlWriteOutbox.get("redisPending")))
                .mysqlWriteOutboxMysqlWritten(readNullableInteger(mysqlWriteOutbox.get("mysqlWritten")))
                .mysqlWriteOutboxRedisRemaining(readNullableInteger(mysqlWriteOutbox.get("redisRemaining")))
                .caseMaterializationRedisPending(readNullableInteger(caseMaterialization.get("redisPending")))
                .caseMaterializationMysqlWritten(readNullableInteger(caseMaterialization.get("mysqlWritten")))
                .caseMaterializationRedisRemaining(readNullableInteger(caseMaterialization.get("redisRemaining")))
                .build();
    }

    private Path resolvePath(String configuredPath) {
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
        return Path.of(System.getProperty("user.dir")).resolve(configuredPath).normalize();
    }

    private Map<String, Object> readJsonFile(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> readJsonObject(String payload) {
        if (!StringUtils.hasText(payload)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<AiPersistenceHistoryEntry> readHistoryEntries(Path indexPath, int limit) {
        if (!Files.exists(indexPath)) {
            return List.of();
        }
        Map<String, Object> payload = readJsonFile(indexPath);
        Object rawEntries = payload.get("entries");
        if (!(rawEntries instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .map(this::toHistoryEntry)
                .limit(Math.max(limit, 0))
                .toList();
    }

    private AiPersistenceHistoryEntry findHistoryEntry(Long occurredAt) {
        if (occurredAt == null) {
            return null;
        }
        return readHistoryEntries(resolvePath(historyIndexPath), Integer.MAX_VALUE).stream()
                .filter(item -> occurredAt.equals(item.getOccurredAt()))
                .findFirst()
                .orElseGet(() -> {
                    AiControlPlaneDrainStatusView status = getStatus();
                    AiControlPlaneDrainReportSummary report = status == null ? null : status.getReport();
                    AiControlPlaneDrainExecutionSummary lastExecution = status == null ? null : status.getLastExecution();
                    Long fallbackOccurredAt = lastExecution == null ? (report == null ? null : report.getExecutedAt()) : lastExecution.getExecutedAt();
                    if (report != null && Boolean.TRUE.equals(report.getExists()) && occurredAt.equals(fallbackOccurredAt)) {
                        return AiPersistenceHistoryEntry.builder()
                                .reportType(DRAIN_AUDIT_EVENT_TYPE)
                                .occurredAt(fallbackOccurredAt)
                                .reportPath(report.getPath())
                                .operator(lastExecution == null ? report.getOperator() : lastExecution.getOperator())
                                .accepted(lastExecution == null ? report.getAccepted() : lastExecution.getAccepted())
                                .dryRun(lastExecution == null ? report.getDryRun() : lastExecution.getDryRun())
                                .message(lastExecution == null ? report.getMessage() : lastExecution.getMessage())
                                .build();
                    }
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findStore(Object stores, String name) {
        if (!(stores instanceof List<?> list)) {
            return Map.of();
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map && name.equals(String.valueOf(map.get("name")))) {
                return (Map<String, Object>) map;
            }
        }
        return Map.of();
    }

    private AiPersistenceHistoryEntry toHistoryEntry(Map<String, Object> item) {
        return AiPersistenceHistoryEntry.builder()
                .reportType(readNullableString(item.get("reportType")))
                .occurredAt(readNullableLong(item.get("occurredAt")))
                .reportPath(readNullableString(item.get("reportPath")))
                .operator(readNullableString(item.get("operator")))
                .scene(readNullableString(item.get("scene")))
                .success(readNullableBoolean(item.get("success")))
                .accepted(readNullableBoolean(item.get("accepted")))
                .dryRun(readNullableBoolean(item.get("dryRun")))
                .message(readNullableString(item.get("message")))
                .build();
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(item -> String.valueOf(item))
                .filter(text -> StringUtils.hasText(text))
                .map(text -> text.trim())
                .toList();
    }

    private Integer readNullableInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long readNullableLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Boolean readNullableBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private String readNullableString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
