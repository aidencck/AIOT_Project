package com.aiot.rule.service;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.rule.dto.DashboardOverviewResponse;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.model.WorkOrderRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OpsClosureService {
    private static final String ALARM_STORE_KEY = "aiot:ops:alarms";
    private static final String WORK_ORDER_STORE_KEY = "aiot:ops:work-orders";
    private static final String AUDIT_STORE_KEY = "aiot:ops:audits";
    private static final String DEVICE_STATUS_KEY = "aiot:ops:device-status";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public OpsClosureService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void refreshDeviceStatus(DeviceEvent event) {
        if (event == null || event.getEventType() == null || !StringUtils.hasText(event.getDeviceId())) {
            return;
        }
        if (event.getEventType() == DeviceEventType.DEVICE_ONLINE) {
            redisTemplate.opsForHash().put(DEVICE_STATUS_KEY, event.getDeviceId(), "ONLINE");
        } else if (event.getEventType() == DeviceEventType.DEVICE_OFFLINE) {
            redisTemplate.opsForHash().put(DEVICE_STATUS_KEY, event.getDeviceId(), "OFFLINE");
        }
    }

    public void createAlarmAndWorkOrder(RuleDefinition rule, DeviceEvent event, String actionPayload) {
        long now = System.currentTimeMillis();
        String level = parseLevel(actionPayload);
        boolean autoCreateWorkOrder = parseAutoCreateWorkOrder(actionPayload);

        AlarmRecord alarm = AlarmRecord.builder()
                .alarmId(UUID.randomUUID().toString())
                .ruleId(rule.getRuleId())
                .eventId(event.getEventId())
                .deviceId(event.getDeviceId())
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

    public List<AlarmRecord> listAlarms(String status, String deviceId) {
        return redisTemplate.opsForHash().values(ALARM_STORE_KEY).stream()
                .map(v -> fromJson(v, AlarmRecord.class))
                .filter(java.util.Objects::nonNull)
                .filter(a -> !StringUtils.hasText(status) || status.equalsIgnoreCase(a.getStatus()))
                .filter(a -> !StringUtils.hasText(deviceId) || deviceId.equals(a.getDeviceId()))
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

    public List<WorkOrderRecord> listWorkOrders(String status, String assignee) {
        return redisTemplate.opsForHash().values(WORK_ORDER_STORE_KEY).stream()
                .map(v -> fromJson(v, WorkOrderRecord.class))
                .filter(java.util.Objects::nonNull)
                .filter(w -> !StringUtils.hasText(status) || status.equalsIgnoreCase(w.getStatus()))
                .filter(w -> !StringUtils.hasText(assignee) || assignee.equals(w.getAssignee()))
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
        Map<Object, Object> deviceStatusMap = redisTemplate.opsForHash().entries(DEVICE_STATUS_KEY);
        int online = 0;
        int offline = 0;
        for (Object value : deviceStatusMap.values()) {
            if ("ONLINE".equals(String.valueOf(value))) {
                online++;
            } else if ("OFFLINE".equals(String.valueOf(value))) {
                offline++;
            }
        }

        LocalDate today = LocalDate.now();
        List<AlarmRecord> alarms = listAlarms(null, null);
        int todayAlarms = (int) alarms.stream().filter(a -> isSameDay(a.getCreatedAt(), today)).count();

        List<WorkOrderRecord> workOrders = listWorkOrders(null, null);
        long pending = workOrders.stream().filter(w -> !"RESOLVED".equalsIgnoreCase(w.getStatus())).count();
        long slaBreachedCount = workOrders.stream().filter(w -> Boolean.TRUE.equals(w.getSlaBreached())).count();
        long resolved = workOrders.stream().filter(w -> "RESOLVED".equalsIgnoreCase(w.getStatus())).count();
        long oneTimeResolved = workOrders.stream()
                .filter(w -> "RESOLVED".equalsIgnoreCase(w.getStatus()) && StringUtils.hasText(w.getResult()) && !w.getResult().contains("返工"))
                .count();
        double oneTimeResolveRate = resolved == 0 ? 1.0 : (double) oneTimeResolved / (double) resolved;

        return DashboardOverviewResponse.builder()
                .onlineDeviceCount(online)
                .offlineDeviceCount(offline)
                .todayAlarmCount(todayAlarms)
                .pendingWorkOrderCount((int) (pending + slaBreachedCount))
                .oneTimeResolveRate(oneTimeResolveRate)
                .build();
    }

    public Integer checkAndMarkSlaBreached() {
        List<WorkOrderRecord> workOrders = listWorkOrders(null, null);
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
        return redisTemplate.opsForHash().values(AUDIT_STORE_KEY).stream()
                .map(v -> fromJson(v, AuditRecord.class))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(AuditRecord::getCreatedAt, Comparator.nullsLast(Long::compareTo)).reversed())
                .limit(safeLimit)
                .collect(Collectors.toList());
    }

    private void saveAlarm(AlarmRecord alarm) {
        redisTemplate.opsForHash().put(ALARM_STORE_KEY, alarm.getAlarmId(), toJson(alarm));
    }

    private AlarmRecord getAlarm(String alarmId) {
        Object raw = redisTemplate.opsForHash().get(ALARM_STORE_KEY, alarmId);
        return fromJson(raw, AlarmRecord.class);
    }

    private void saveWorkOrder(WorkOrderRecord workOrder) {
        redisTemplate.opsForHash().put(WORK_ORDER_STORE_KEY, workOrder.getWorkOrderId(), toJson(workOrder));
    }

    private WorkOrderRecord getWorkOrder(String workOrderId) {
        Object raw = redisTemplate.opsForHash().get(WORK_ORDER_STORE_KEY, workOrderId);
        return fromJson(raw, WorkOrderRecord.class);
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
        redisTemplate.opsForHash().put(AUDIT_STORE_KEY, audit.getAuditId(), toJson(audit));
    }

    private boolean isSameDay(Long timestamp, LocalDate day) {
        if (timestamp == null) {
            return false;
        }
        LocalDate date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        return day.equals(date);
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private <T> T fromJson(Object raw, Class<T> clazz) {
        if (!(raw instanceof String json) || !StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("反序列化失败, class={}", clazz.getSimpleName(), e);
            return null;
        }
    }
}
