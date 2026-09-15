package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryEntry;
import com.aiot.rule.dto.AiBusinessLiveFlowReportSummary;
import com.aiot.rule.dto.AiBusinessLiveFlowStatusView;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Service
public class AiBusinessLiveFlowStatusProvider {

    private final ObjectMapper objectMapper;
    private final String reportPath;
    private final String historyIndexPath;

    public AiBusinessLiveFlowStatusProvider(ObjectMapper objectMapper,
                                            @Value("${aiot.ai.persistence.business-live-flow.report-path:training/data/reports/persistence/ai_business_live_flow.json}")
                                            String reportPath,
                                            @Value("${aiot.ai.persistence.business-live-flow.history-index-path:training/data/reports/persistence/ai_business_live_flow_index.json}")
                                            String historyIndexPath) {
        this.objectMapper = objectMapper;
        this.reportPath = reportPath;
        this.historyIndexPath = historyIndexPath;
    }

    public AiBusinessLiveFlowStatusView getStatus() {
        Path resolvedReportPath = resolvePath(reportPath);
        return AiBusinessLiveFlowStatusView.builder()
                .report(resolveReportSummary(resolvedReportPath))
                .build();
    }

    public List<AiPersistenceHistoryEntry> getRecentHistory(int limit) {
        List<AiPersistenceHistoryEntry> history = readHistoryEntries(resolvePath(historyIndexPath), limit);
        if (!history.isEmpty()) {
            return history;
        }
        AiBusinessLiveFlowReportSummary report = resolveReportSummary(resolvePath(reportPath));
        if (!Boolean.TRUE.equals(report.getExists())) {
            return List.of();
        }
        return List.of(AiPersistenceHistoryEntry.builder()
                .reportType("BUSINESS_LIVE_FLOW")
                .occurredAt(report.getVerifiedAt())
                .reportPath(report.getPath())
                .scene(report.getScene())
                .success(report.getSuccess())
                .build());
    }

    public AiPersistenceHistoryDetailResp getHistoryDetail(Long occurredAt) {
        AiPersistenceHistoryEntry entry = findHistoryEntry(occurredAt);
        if (entry == null || entry.getReportPath() == null) {
            return AiPersistenceHistoryDetailResp.builder()
                    .reportType(AiPersistenceHistoryQueryService.BUSINESS_LIVE_FLOW_REPORT_TYPE)
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

    private AiBusinessLiveFlowReportSummary resolveReportSummary(Path resolvedReportPath) {
        if (!Files.exists(resolvedReportPath)) {
            return AiBusinessLiveFlowReportSummary.builder()
                    .exists(Boolean.FALSE)
                    .path(resolvedReportPath.toAbsolutePath().normalize().toString())
                    .build();
        }
        Map<String, Object> payload = readJsonFile(resolvedReportPath);
        Map<String, Object> diagnosisRequest = getMap(getMap(payload, "requests"), "diagnosis");
        Map<String, Object> diagnosisResponse = getMap(getMap(payload, "responses"), "diagnosis");
        Map<String, Object> feedbackResponse = getMap(getMap(payload, "responses"), "feedback");
        Map<String, Object> mysql = getMap(payload, "mysql");
        Map<String, Object> redis = getMap(payload, "redis");
        return AiBusinessLiveFlowReportSummary.builder()
                .exists(Boolean.TRUE)
                .path(resolvedReportPath.toAbsolutePath().normalize().toString())
                .verifiedAt(readNullableLong(payload.get("verifiedAt")))
                .scene(readNullableString(payload.get("scene")))
                .success(readNullableBoolean(payload.get("success")))
                .deviceId(readNullableString(diagnosisRequest.get("deviceId")))
                .globalDeviceId(firstNonBlank(
                        readNullableString(diagnosisRequest.get("globalDeviceId")),
                        readNullableString(diagnosisRequest.get("deviceId"))
                ))
                .authIdentity(firstNonBlank(
                        readNullableString(diagnosisRequest.get("authIdentity")),
                        readNullableString(diagnosisRequest.get("deviceSn"))
                ))
                .deviceSn(readNullableString(diagnosisRequest.get("deviceSn")))
                .eventId(readNullableString(diagnosisRequest.get("eventId")))
                .diagnosisId(readNullableString(diagnosisResponse.get("diagnosisId")))
                .feedbackId(readNullableString(feedbackResponse.get("feedbackId")))
                .caseId(readNullableString(feedbackResponse.get("caseId")))
                .mysqlWriteOutboxCount(readNullableInteger(mysql.get("mysqlWriteOutboxCount")))
                .caseMaterializationTaskCount(readNullableInteger(mysql.get("caseMaterializationTaskCount")))
                .diagnosisMirrorExists(readNullableBoolean(redis.get("diagnosisMirrorExists")))
                .feedbackMirrorExists(readNullableBoolean(redis.get("feedbackMirrorExists")))
                .caseMirrorExists(readNullableBoolean(redis.get("caseMirrorExists")))
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
                    AiBusinessLiveFlowReportSummary report = resolveReportSummary(resolvePath(reportPath));
                    if (Boolean.TRUE.equals(report.getExists()) && occurredAt.equals(report.getVerifiedAt())) {
                        return AiPersistenceHistoryEntry.builder()
                                .reportType(AiPersistenceHistoryQueryService.BUSINESS_LIVE_FLOW_REPORT_TYPE)
                                .occurredAt(report.getVerifiedAt())
                                .reportPath(report.getPath())
                                .scene(report.getScene())
                                .success(report.getSuccess())
                                .build();
                    }
                    return null;
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        if (source == null) {
            return Map.of();
        }
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
