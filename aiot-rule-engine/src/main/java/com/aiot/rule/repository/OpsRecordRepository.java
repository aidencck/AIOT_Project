package com.aiot.rule.repository;

import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.WorkOrderRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class OpsRecordRepository {

    private static final String ALARM_STORE_KEY = "aiot:ops:alarms";
    private static final String WORK_ORDER_STORE_KEY = "aiot:ops:work-orders";
    private static final String AUDIT_STORE_KEY = "aiot:ops:audits";
    private static final String DEVICE_STATUS_KEY = "aiot:ops:device-status";

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public OpsRecordRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void saveDeviceStatus(String deviceId, String status) {
        redisTemplate.opsForHash().put(DEVICE_STATUS_KEY, deviceId, status);
    }

    public Map<Object, Object> findAllDeviceStatuses() {
        return redisTemplate.opsForHash().entries(DEVICE_STATUS_KEY);
    }

    public void saveAlarm(AlarmRecord alarm) {
        redisTemplate.opsForHash().put(ALARM_STORE_KEY, alarm.getAlarmId(), toJson(alarm));
    }

    public AlarmRecord findAlarmById(String alarmId) {
        Object raw = redisTemplate.opsForHash().get(ALARM_STORE_KEY, alarmId);
        return fromJson(raw, AlarmRecord.class);
    }

    public List<AlarmRecord> findAllAlarms() {
        return redisTemplate.opsForHash().values(ALARM_STORE_KEY).stream()
                .map(v -> fromJson(v, AlarmRecord.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void saveWorkOrder(WorkOrderRecord workOrder) {
        redisTemplate.opsForHash().put(WORK_ORDER_STORE_KEY, workOrder.getWorkOrderId(), toJson(workOrder));
    }

    public WorkOrderRecord findWorkOrderById(String workOrderId) {
        Object raw = redisTemplate.opsForHash().get(WORK_ORDER_STORE_KEY, workOrderId);
        return fromJson(raw, WorkOrderRecord.class);
    }

    public List<WorkOrderRecord> findAllWorkOrders() {
        return redisTemplate.opsForHash().values(WORK_ORDER_STORE_KEY).stream()
                .map(v -> fromJson(v, WorkOrderRecord.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void saveAudit(AuditRecord audit) {
        redisTemplate.opsForHash().put(AUDIT_STORE_KEY, audit.getAuditId(), toJson(audit));
    }

    public List<AuditRecord> findAllAudits() {
        return redisTemplate.opsForHash().values(AUDIT_STORE_KEY).stream()
                .map(v -> fromJson(v, AuditRecord.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
