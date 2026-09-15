package com.aiot.rule.service;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.rule.client.DeviceStatusSyncClient;
import com.aiot.rule.client.DeviceStatusSummaryClient;
import com.aiot.rule.dto.AiPersistenceStatusResponse;
import com.aiot.rule.dto.DashboardOverviewResponse;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.model.WorkOrderRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiot.rule.repository.OpsRecordRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpsClosureService {
    private final OpsRecordRepository opsRecordRepository;
    private final ObjectMapper objectMapper;
    private final AiPersistenceStatusService aiPersistenceStatusService;
    private final DeviceIdentityCompatService deviceIdentityCompatService;
    private final DeviceStatusSummaryClient deviceStatusSummaryClient;
    private final DeviceStatusSyncClient deviceStatusSyncClient;

    public OpsClosureService(OpsRecordRepository opsRecordRepository,
                             ObjectMapper objectMapper,
                             AiPersistenceStatusService aiPersistenceStatusService,
                             DeviceIdentityCompatService deviceIdentityCompatService,
                             DeviceStatusSummaryClient deviceStatusSummaryClient,
                             DeviceStatusSyncClient deviceStatusSyncClient) {
        this.opsRecordRepository = opsRecordRepository;
        this.objectMapper = objectMapper;
        this.aiPersistenceStatusService = aiPersistenceStatusService;
        this.deviceIdentityCompatService = deviceIdentityCompatService;
        this.deviceStatusSummaryClient = deviceStatusSummaryClient;
        this.deviceStatusSyncClient = deviceStatusSyncClient;
    }

    public void refreshDeviceStatus(DeviceEvent event) {
        if (event == null || event.getEventType() == null || !StringUtils.hasText(event.getDeviceId())) {
            return;
        }
        int status;
        if (DeviceEventType.DEVICE_ONLINE == event.getEventType()) {
            status = 1;
        } else if (DeviceEventType.DEVICE_OFFLINE == event.getEventType()) {
            status = 2;
        } else {
            return;
        }
        try {
            deviceStatusSyncClient.syncStatus(event.getDeviceId(), status);
        } catch (Exception ex) {
            log.warn("同步设备状态失败, deviceId={}, status={}", event.getDeviceId(), status, ex);
        }
    }

    public Map<String, Object> getDeviceOpsSummary(String deviceIdentity) {
        List<AlarmRecord> alarms = listAlarms(null, deviceIdentity);
        List<WorkOrderRecord> workOrders = listWorkOrders(null, null, deviceIdentity);

        int activeAlarmCount = 0;
        Long lastAlarmAt = null;
        for (AlarmRecord alarm : alarms) {
            if (!"RESOLVED".equalsIgnoreCase(alarm.getStatus())) {
                activeAlarmCount++;
            }
            if (alarm.getCreatedAt() != null) {
                lastAlarmAt = lastAlarmAt == null ? alarm.getCreatedAt() : Math.max(lastAlarmAt, alarm.getCreatedAt());
            }
        }

        int openWorkOrderCount = 0;
        int slaBreachedCount = 0;
        Long lastWorkOrderAt = null;
        for (WorkOrderRecord workOrder : workOrders) {
            if (!"RESOLVED".equalsIgnoreCase(workOrder.getStatus())) {
                openWorkOrderCount++;
            }
            if (Boolean.TRUE.equals(workOrder.getSlaBreached())) {
                slaBreachedCount++;
            }
            if (workOrder.getCreatedAt() != null) {
                lastWorkOrderAt = lastWorkOrderAt == null ? workOrder.getCreatedAt() : Math.max(lastWorkOrderAt, workOrder.getCreatedAt());
            }
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("alarmCount", alarms.size());
        summary.put("activeAlarmCount", activeAlarmCount);
        summary.put("workOrderCount", workOrders.size());
        summary.put("openWorkOrderCount", openWorkOrderCount);
        summary.put("slaBreachedCount", slaBreachedCount);
        summary.put("lastAlarmAt", lastAlarmAt);
        summary.put("lastWorkOrderAt", lastWorkOrderAt);
        return summary;
    }

    public void createAlarmAndWorkOrder(RuleDefinition rule, DeviceEvent event, String actionPayload) {
        long now = System.currentTimeMillis();
        String level = parseLevel(actionPayload);
        boolean autoCreateWorkOrder = parseAutoCreateWorkOrder(actionPayload);
        DeviceIdentityCompatService.DeviceIdentitySnapshot deviceIdentitySnapshot = deviceIdentityCompatService.resolveSnapshot(
                event.getDeviceId(),
                null,
                null,
                null
        );

        AlarmRecord alarm = AlarmRecord.builder()
                .alarmId(UUID.randomUUID().toString())
                .ruleId(rule.getRuleId())
                .eventId(event.getEventId())
                .deviceId(event.getDeviceId())
                .globalDeviceId(deviceIdentitySnapshot.globalDeviceId())
                .authIdentity(deviceIdentitySnapshot.authIdentity())
                .deviceSn(deviceIdentitySnapshot.deviceSn())
                .eventType(event.getEventType() == null ? "UNKNOWN" : event.getEventType().name())
                .level(level)
                .status("NEW")
                .occurredAt(event.getTimestamp() == null ? now : event.getTimestamp())
                .createdAt(now)
                .updatedAt(now)
                .build();
        saveAlarm(alarm);
        appendAudit("ALARM_CREATED", "system", alarm.getAlarmId(), "ruleId=" + rule.getRuleId());

        if (autoCreateWorkOrder) {
            int slaMinutes = "P1".equals(level) ? 15 : 60;
            WorkOrderRecord workOrder = WorkOrderRecord.builder()
                    .workOrderId(UUID.randomUUID().toString())
                    .alarmId(alarm.getAlarmId())
                    .deviceId(alarm.getDeviceId())
                    .globalDeviceId(alarm.getGlobalDeviceId())
                    .authIdentity(alarm.getAuthIdentity())
                    .deviceSn(alarm.getDeviceSn())
                    .priority(level)
                    .status("OPEN")
                    .slaMinutes(slaMinutes)
                    .dueAt(now + slaMinutes * 60L * 1000L)
                    .slaBreached(Boolean.FALSE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            saveWorkOrder(workOrder);
            appendAudit("WORK_ORDER_CREATED", "system", workOrder.getWorkOrderId(), "alarmId=" + alarm.getAlarmId());
        }
    }

    public List<AlarmRecord> listAlarms(String status, String deviceIdentity) {
        return opsRecordRepository.findAllAlarms().stream()
                .map(this::enrichAlarmIfNecessary)
                .filter(a -> !StringUtils.hasText(status) || status.equalsIgnoreCase(a.getStatus()))
                .filter(a -> deviceIdentityCompatService.matches(
                        deviceIdentity,
                        a.getDeviceId(),
                        a.getGlobalDeviceId(),
                        a.getAuthIdentity(),
                        a.getDeviceSn()
                ))
                .sorted(Comparator.comparing(AlarmRecord::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .collect(Collectors.toList());
    }

    public void acknowledgeAlarm(String alarmId, String operator) {
        AlarmRecord alarm = getAlarm(alarmId);
        if (alarm == null) {
            throw new IllegalArgumentException("告警不存在: " + alarmId);
        }
        long now = System.currentTimeMillis();
        alarm.setStatus("ACKED");
        alarm.setAcknowledgedBy(operator);
        alarm.setAcknowledgedAt(now);
        alarm.setUpdatedAt(now);
        saveAlarm(alarm);
        appendAudit("ALARM_ACKED", operator, alarmId, "acknowledged");
    }

    public void resolveAlarm(String alarmId, String operator) {
        AlarmRecord alarm = getAlarm(alarmId);
        if (alarm == null) {
            throw new IllegalArgumentException("告警不存在: " + alarmId);
        }
        long now = System.currentTimeMillis();
        alarm.setStatus("RESOLVED");
        alarm.setResolvedAt(now);
        alarm.setResolvedBy(operator);
        alarm.setUpdatedAt(now);
        saveAlarm(alarm);
        appendAudit("ALARM_RESOLVED", operator, alarmId, "resolved");
    }

    public List<WorkOrderRecord> listWorkOrders(String status, String assignee, String deviceIdentity) {
        return opsRecordRepository.findAllWorkOrders().stream()
                .map(this::enrichWorkOrderIfNecessary)
                .filter(w -> !StringUtils.hasText(status) || status.equalsIgnoreCase(w.getStatus()))
                .filter(w -> !StringUtils.hasText(assignee) || assignee.equals(w.getAssignee()))
                .filter(w -> deviceIdentityCompatService.matches(
                        deviceIdentity,
                        w.getDeviceId(),
                        w.getGlobalDeviceId(),
                        w.getAuthIdentity(),
                        w.getDeviceSn()
                ))
                .sorted(Comparator.comparing(WorkOrderRecord::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .collect(Collectors.toList());
    }

    public void claimWorkOrder(String workOrderId, String assignee) {
        WorkOrderRecord workOrder = getWorkOrder(workOrderId);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在: " + workOrderId);
        }
        long now = System.currentTimeMillis();
        workOrder.setStatus("IN_PROGRESS");
        workOrder.setAssignee(assignee);
        if (workOrder.getRespondedAt() == null) {
            workOrder.setRespondedAt(now);
        }
        workOrder.setUpdatedAt(now);
        saveWorkOrder(workOrder);
        appendAudit("WORK_ORDER_CLAIMED", assignee, workOrderId, "claimed");
    }

    public void resolveWorkOrder(String workOrderId, String operator, String result) {
        WorkOrderRecord workOrder = getWorkOrder(workOrderId);
        if (workOrder == null) {
            throw new IllegalArgumentException("工单不存在: " + workOrderId);
        }
        long now = System.currentTimeMillis();
        workOrder.setStatus("RESOLVED");
        workOrder.setResolvedAt(now);
        workOrder.setResult(result);
        workOrder.setUpdatedAt(now);
        if (!StringUtils.hasText(workOrder.getAssignee())) {
            workOrder.setAssignee(operator);
        }
        saveWorkOrder(workOrder);
        appendAudit("WORK_ORDER_RESOLVED", operator, workOrderId, result);
    }

    public DashboardOverviewResponse getOverview() {
        DeviceStatusSummaryResp statusSummary = deviceStatusSummaryClient.getStatusSummary();
        int online = toInt(statusSummary.getOnlineCount());
        int offline = toInt(statusSummary.getOfflineCount());

        LocalDate today = LocalDate.now();
        List<AlarmRecord> alarms = listAlarms(null, null);
        int todayAlarms = (int) alarms.stream().filter(a -> isSameDay(a.getCreatedAt(), today)).count();

        List<WorkOrderRecord> workOrders = listWorkOrders(null, null, null);
        long pending = workOrders.stream().filter(w -> !"RESOLVED".equalsIgnoreCase(w.getStatus())).count();
        long slaBreachedCount = workOrders.stream().filter(w -> Boolean.TRUE.equals(w.getSlaBreached())).count();
        long resolved = workOrders.stream().filter(w -> "RESOLVED".equalsIgnoreCase(w.getStatus())).count();
        long oneTimeResolved = workOrders.stream()
                .filter(w -> "RESOLVED".equalsIgnoreCase(w.getStatus()) && StringUtils.hasText(w.getResult()) && !w.getResult().contains("返工"))
                .count();
        double oneTimeResolveRate = resolved == 0 ? 1.0 : (double) oneTimeResolved / (double) resolved;
        AiPersistenceStatusResponse aiPersistenceStatus = aiPersistenceStatusService.getStatus();
        var businessLiveFlow = aiPersistenceStatus.getBusinessLiveFlowStatus();
        var businessLiveFlowReport = deviceIdentityCompatService.enrichBusinessLiveFlow(
                businessLiveFlow == null ? null : businessLiveFlow.getReport()
        );
        var drainStatus = aiPersistenceStatus.getControlPlaneDrainStatus();
        var drainReport = drainStatus == null ? null : drainStatus.getReport();

        return DashboardOverviewResponse.builder()
                .onlineDeviceCount(online)
                .offlineDeviceCount(offline)
                .todayAlarmCount(todayAlarms)
                .pendingWorkOrderCount((int) (pending + slaBreachedCount))
                .oneTimeResolveRate(oneTimeResolveRate)
                .aiPersistenceMysqlEnabled(aiPersistenceStatus.getMysqlEnabled())
                .aiPersistenceReadMode(aiPersistenceStatus.getReadMode())
                .aiPersistenceConfiguredReadMode(aiPersistenceStatus.getConfiguredReadMode())
                .aiPersistenceMysqlReady(aiPersistenceStatus.getMysqlReady())
                .aiPersistenceMysqlCutoverReady(aiPersistenceStatus.getMysqlCutoverReady())
                .aiPersistenceMysqlCutoverBlockReason(aiPersistenceStatus.getMysqlCutoverBlockReason())
                .aiPersistenceBackfillManifestExists(aiPersistenceStatus.getBackfillManifestExists())
                .aiPersistenceBackfillWrittenTotal(aiPersistenceStatus.getBackfillWrittenTotal())
                .aiPersistenceConsistencyReportExists(aiPersistenceStatus.getConsistencyReportExists())
                .aiPersistenceConsistencyPassed(aiPersistenceStatus.getConsistencyPassed())
                .aiPersistenceConsistencyTotalMismatch(aiPersistenceStatus.getConsistencyTotalMismatch())
                .aiPersistenceConstraintsReportExists(aiPersistenceStatus.getConstraintsReportExists())
                .aiPersistenceConstraintsGatePassed(aiPersistenceStatus.getConstraintsGatePassed())
                .aiPersistenceConstraintsFailedCount(aiPersistenceStatus.getConstraintsFailedCount())
                .aiPersistenceMigrationGatePassed(aiPersistenceStatus.getMigrationGatePassed())
                .aiPersistenceMysqlWriteOutboxStoreMode(aiPersistenceStatus.getMysqlWriteOutboxStoreMode())
                .aiPersistenceMysqlWriteOutboxLegacyRedisPendingCount(aiPersistenceStatus.getMysqlWriteOutboxLegacyRedisPendingCount())
                .aiPersistenceMysqlWriteOutboxReplayEnabled(aiPersistenceStatus.getMysqlWriteOutboxReplayEnabled())
                .aiPersistenceMysqlWriteOutboxReplayBatchSize(aiPersistenceStatus.getMysqlWriteOutboxReplayBatchSize())
                .aiPersistenceMysqlWriteOutboxReplayFixedDelayMs(aiPersistenceStatus.getMysqlWriteOutboxReplayFixedDelayMs())
                .aiPersistenceMysqlWriteOutboxPendingCount(aiPersistenceStatus.getMysqlWriteOutboxPendingCount())
                .aiPersistenceMysqlWriteOutboxOldestAgeSeconds(aiPersistenceStatus.getMysqlWriteOutboxOldestAgeSeconds())
                .aiPersistenceCaseMaterializationStoreMode(aiPersistenceStatus.getCaseMaterializationStoreMode())
                .aiPersistenceCaseMaterializationLegacyRedisPendingCount(aiPersistenceStatus.getCaseMaterializationLegacyRedisPendingCount())
                .aiPersistenceCaseMaterializationReplayEnabled(aiPersistenceStatus.getCaseMaterializationReplayEnabled())
                .aiPersistenceCaseMaterializationReplayBatchSize(aiPersistenceStatus.getCaseMaterializationReplayBatchSize())
                .aiPersistenceCaseMaterializationReplayFixedDelayMs(aiPersistenceStatus.getCaseMaterializationReplayFixedDelayMs())
                .aiCaseMaterializationPendingCount(aiPersistenceStatus.getCaseMaterializationPendingCount())
                .aiCaseMaterializationOldestAgeSeconds(aiPersistenceStatus.getCaseMaterializationOldestAgeSeconds())
                .aiPersistenceControlPlaneRedisDrainCompleted(aiPersistenceStatus.getControlPlaneRedisDrainCompleted())
                .aiPersistenceBusinessLiveFlowReportExists(businessLiveFlowReport == null ? Boolean.FALSE : businessLiveFlowReport.getExists())
                .aiPersistenceBusinessLiveFlowReportPath(businessLiveFlowReport == null ? null : businessLiveFlowReport.getPath())
                .aiPersistenceBusinessLiveFlowVerifiedAt(businessLiveFlowReport == null ? null : businessLiveFlowReport.getVerifiedAt())
                .aiPersistenceBusinessLiveFlowScene(businessLiveFlowReport == null ? null : businessLiveFlowReport.getScene())
                .aiPersistenceBusinessLiveFlowSuccess(businessLiveFlowReport == null ? null : businessLiveFlowReport.getSuccess())
                .aiPersistenceBusinessLiveFlowDeviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceId())
                .aiPersistenceBusinessLiveFlowGlobalDeviceId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getGlobalDeviceId())
                .aiPersistenceBusinessLiveFlowAuthIdentity(businessLiveFlowReport == null ? null : businessLiveFlowReport.getAuthIdentity())
                .aiPersistenceBusinessLiveFlowDeviceSn(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDeviceSn())
                .aiPersistenceBusinessLiveFlowEventId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getEventId())
                .aiPersistenceBusinessLiveFlowDiagnosisId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisId())
                .aiPersistenceBusinessLiveFlowFeedbackId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackId())
                .aiPersistenceBusinessLiveFlowCaseId(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseId())
                .aiPersistenceBusinessLiveFlowMysqlWriteOutboxCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getMysqlWriteOutboxCount())
                .aiPersistenceBusinessLiveFlowCaseMaterializationTaskCount(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMaterializationTaskCount())
                .aiPersistenceBusinessLiveFlowDiagnosisMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getDiagnosisMirrorExists())
                .aiPersistenceBusinessLiveFlowFeedbackMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getFeedbackMirrorExists())
                .aiPersistenceBusinessLiveFlowCaseMirrorExists(businessLiveFlowReport == null ? null : businessLiveFlowReport.getCaseMirrorExists())
                .aiPersistenceControlPlaneLastDrainAt(aiPersistenceStatus.getControlPlaneLastDrainAt())
                .aiPersistenceControlPlaneLastDrainOperator(aiPersistenceStatus.getControlPlaneLastDrainOperator())
                .aiPersistenceControlPlaneLastDrainDryRun(aiPersistenceStatus.getControlPlaneLastDrainDryRun())
                .aiPersistenceControlPlaneLastDrainAccepted(aiPersistenceStatus.getControlPlaneLastDrainAccepted())
                .aiPersistenceControlPlaneLastDrainMessage(aiPersistenceStatus.getControlPlaneLastDrainMessage())
                .aiPersistenceControlPlaneDrainReportExists(drainReport == null ? Boolean.FALSE : drainReport.getExists())
                .aiPersistenceControlPlaneDrainReportPath(drainReport == null ? null : drainReport.getPath())
                .aiPersistenceControlPlaneDrainReportSource(drainReport == null ? null : drainReport.getSource())
                .aiPersistenceControlPlaneDrainReportExecutedAt(drainReport == null ? null : drainReport.getExecutedAt())
                .aiPersistenceControlPlaneDrainReportOperator(drainReport == null ? null : drainReport.getOperator())
                .aiPersistenceControlPlaneDrainReportDryRun(drainReport == null ? null : drainReport.getDryRun())
                .aiPersistenceControlPlaneDrainReportAccepted(drainReport == null ? null : drainReport.getAccepted())
                .aiPersistenceControlPlaneDrainReportBatchSize(drainReport == null ? null : drainReport.getBatchSize())
                .aiPersistenceControlPlaneDrainReportRequestedStores(joinRequestedStores(drainReport == null ? null : drainReport.getRequestedStores()))
                .aiPersistenceControlPlaneDrainReportMessage(drainReport == null ? null : drainReport.getMessage())
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisPending(drainReport == null ? null : drainReport.getMysqlWriteOutboxRedisPending())
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxMysqlWritten(drainReport == null ? null : drainReport.getMysqlWriteOutboxMysqlWritten())
                .aiPersistenceControlPlaneDrainReportMysqlWriteOutboxRedisRemaining(drainReport == null ? null : drainReport.getMysqlWriteOutboxRedisRemaining())
                .aiPersistenceControlPlaneDrainReportCaseMaterializationRedisPending(drainReport == null ? null : drainReport.getCaseMaterializationRedisPending())
                .aiPersistenceControlPlaneDrainReportCaseMaterializationMysqlWritten(drainReport == null ? null : drainReport.getCaseMaterializationMysqlWritten())
                .aiPersistenceControlPlaneDrainReportCaseMaterializationRedisRemaining(drainReport == null ? null : drainReport.getCaseMaterializationRedisRemaining())
                .build();
    }

    private String joinRequestedStores(List<String> requestedStores) {
        if (requestedStores == null || requestedStores.isEmpty()) {
            return null;
        }
        return requestedStores.stream()
                .filter(Objects::nonNull)
                .map(store -> store.trim())
                .filter(store -> StringUtils.hasText(store))
                .collect(Collectors.joining(","));
    }

    public Integer checkAndMarkSlaBreached() {
        List<WorkOrderRecord> workOrders = listWorkOrders(null, null, null);
        long now = System.currentTimeMillis();
        int changed = 0;
        for (WorkOrderRecord workOrder : workOrders) {
            if ("RESOLVED".equalsIgnoreCase(workOrder.getStatus())) {
                continue;
            }
            if (Boolean.TRUE.equals(workOrder.getSlaBreached())) {
                continue;
            }
            if (workOrder.getDueAt() == null || workOrder.getDueAt() >= now) {
                continue;
            }
            workOrder.setSlaBreached(Boolean.TRUE);
            workOrder.setStatus("SLA_BREACHED");
            workOrder.setUpdatedAt(now);
            saveWorkOrder(workOrder);
            appendAudit("WORK_ORDER_SLA_BREACHED", "system", workOrder.getWorkOrderId(), "dueAt=" + workOrder.getDueAt());
            changed++;
        }
        return changed;
    }

    public List<AuditRecord> listAudits(Integer limit) {
        int safeLimit = limit == null || limit <= 0 ? 50 : Math.min(limit, 200);
        return opsRecordRepository.findAllAudits().stream()
                .sorted(Comparator.comparing(AuditRecord::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    private void saveAlarm(AlarmRecord alarm) {
        opsRecordRepository.saveAlarm(alarm);
    }

    private AlarmRecord getAlarm(String alarmId) {
        return opsRecordRepository.findAlarmById(alarmId);
    }

    private void saveWorkOrder(WorkOrderRecord workOrder) {
        opsRecordRepository.saveWorkOrder(workOrder);
    }

    private WorkOrderRecord getWorkOrder(String workOrderId) {
        return opsRecordRepository.findWorkOrderById(workOrderId);
    }

    private AlarmRecord enrichAlarmIfNecessary(AlarmRecord alarm) {
        if (alarm == null) {
            return null;
        }
        String beforeGlobalDeviceId = alarm.getGlobalDeviceId();
        String beforeAuthIdentity = alarm.getAuthIdentity();
        String beforeDeviceSn = alarm.getDeviceSn();
        deviceIdentityCompatService.enrichAlarmRecord(alarm);
        if (!Objects.equals(beforeGlobalDeviceId, alarm.getGlobalDeviceId())
                || !Objects.equals(beforeAuthIdentity, alarm.getAuthIdentity())
                || !Objects.equals(beforeDeviceSn, alarm.getDeviceSn())) {
            saveAlarm(alarm);
        }
        return alarm;
    }

    private WorkOrderRecord enrichWorkOrderIfNecessary(WorkOrderRecord workOrder) {
        if (workOrder == null) {
            return null;
        }
        String beforeGlobalDeviceId = workOrder.getGlobalDeviceId();
        String beforeAuthIdentity = workOrder.getAuthIdentity();
        String beforeDeviceSn = workOrder.getDeviceSn();
        deviceIdentityCompatService.enrichWorkOrderRecord(workOrder);
        if (!Objects.equals(beforeGlobalDeviceId, workOrder.getGlobalDeviceId())
                || !Objects.equals(beforeAuthIdentity, workOrder.getAuthIdentity())
                || !Objects.equals(beforeDeviceSn, workOrder.getDeviceSn())) {
            saveWorkOrder(workOrder);
        }
        return workOrder;
    }

    private void appendAudit(String eventType, String operator, String targetId, String details) {
        AuditRecord audit = AuditRecord.builder()
                .auditId(UUID.randomUUID().toString())
                .eventType(eventType)
                .operator(operator)
                .targetId(targetId)
                .details(details)
                .traceId(MDC.get("traceId"))
                .createdAt(System.currentTimeMillis())
                .build();
        opsRecordRepository.saveAudit(audit);
    }

    private boolean isSameDay(Long timestamp, LocalDate day) {
        if (timestamp == null) {
            return false;
        }
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        return day.equals(date);
    }

    private int toInt(Long value) {
        return value == null ? 0 : value.intValue();
    }

    private String parseLevel(String actionPayload) {
        if (!StringUtils.hasText(actionPayload)) {
            return "P2";
        }
        if (!actionPayload.trim().startsWith("{")) {
            return "P2";
        }
        try {
            Map<?, ?> map = objectMapper.readValue(actionPayload, Map.class);
            Object level = map.get("level");
            String value = level == null ? "P2" : String.valueOf(level).toUpperCase();
            return ("P1".equals(value) || "P2".equals(value)) ? value : "P2";
        } catch (Exception e) {
            return "P2";
        }
    }

    private boolean parseAutoCreateWorkOrder(String actionPayload) {
        if (!StringUtils.hasText(actionPayload) || !actionPayload.trim().startsWith("{")) {
            return true;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(actionPayload, Map.class);
            Object autoCreate = map.get("autoCreateWorkOrder");
            if (autoCreate == null) {
                return true;
            }
            return Boolean.parseBoolean(String.valueOf(autoCreate));
        } catch (Exception e) {
            return true;
        }
    }

}
