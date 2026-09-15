package com.aiot.rule.repository;

import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.AuditRecord;
import com.aiot.rule.model.WorkOrderRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsRecordRepositoryTest {

    private RedisTemplate<String, Object> redisTemplate;
    private HashOperations<String, Object, Object> hashOperations;
    private OpsRecordRepository repository;
    private ObjectMapper objectMapper;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = (RedisTemplate<String, Object>) mock(RedisTemplate.class);
        HashOperations<String, Object, Object> operations = (HashOperations<String, Object, Object>) mock(HashOperations.class);
        hashOperations = operations;
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        objectMapper = new ObjectMapper();
        repository = new OpsRecordRepository(redisTemplate, objectMapper);
    }

    @Test
    void shouldDeserializeAuditRecordsFromRedis() throws Exception {
        AuditRecord record = AuditRecord.builder()
                .auditId("audit-1")
                .eventType("AI_CONTROL_PLANE_REDIS_DRAIN")
                .operator("ops-admin")
                .details("{\"accepted\":true}")
                .createdAt(123456789L)
                .build();
        when(hashOperations.values("aiot:ops:audits"))
                .thenReturn(List.of(objectMapper.writeValueAsString(record)));

        List<AuditRecord> audits = repository.findAllAudits();

        assertEquals(1, audits.size());
        assertEquals("audit-1", audits.get(0).getAuditId());
        assertEquals("ops-admin", audits.get(0).getOperator());
        assertEquals(123456789L, audits.get(0).getCreatedAt());
    }

    @Test
    void shouldDeserializeAlarmRecordsFromRedis() throws Exception {
        AlarmRecord record = AlarmRecord.builder()
                .alarmId("alarm-1")
                .deviceId("device-1")
                .globalDeviceId("g-device-1")
                .authIdentity("auth-device-1")
                .deviceSn("sn-device-1")
                .status("OPEN")
                .createdAt(111L)
                .build();
        when(hashOperations.values("aiot:ops:alarms"))
                .thenReturn(List.of(objectMapper.writeValueAsString(record)));

        List<AlarmRecord> alarms = repository.findAllAlarms();

        assertEquals(1, alarms.size());
        assertEquals("alarm-1", alarms.get(0).getAlarmId());
        assertEquals("device-1", alarms.get(0).getDeviceId());
        assertEquals("g-device-1", alarms.get(0).getGlobalDeviceId());
        assertEquals("auth-device-1", alarms.get(0).getAuthIdentity());
        assertEquals("sn-device-1", alarms.get(0).getDeviceSn());
        assertEquals("OPEN", alarms.get(0).getStatus());
    }

    @Test
    void shouldDeserializeWorkOrdersFromRedis() throws Exception {
        WorkOrderRecord record = WorkOrderRecord.builder()
                .workOrderId("wo-1")
                .alarmId("alarm-1")
                .deviceId("device-1")
                .globalDeviceId("g-device-1")
                .authIdentity("auth-device-1")
                .deviceSn("sn-device-1")
                .status("PENDING")
                .slaBreached(false)
                .build();
        when(hashOperations.values("aiot:ops:work-orders"))
                .thenReturn(List.of(objectMapper.writeValueAsString(record)));

        List<WorkOrderRecord> workOrders = repository.findAllWorkOrders();

        assertEquals(1, workOrders.size());
        assertEquals("wo-1", workOrders.get(0).getWorkOrderId());
        assertEquals("alarm-1", workOrders.get(0).getAlarmId());
        assertEquals("g-device-1", workOrders.get(0).getGlobalDeviceId());
        assertEquals("auth-device-1", workOrders.get(0).getAuthIdentity());
        assertEquals("sn-device-1", workOrders.get(0).getDeviceSn());
        assertFalse(Boolean.TRUE.equals(workOrders.get(0).getSlaBreached()));
    }
}
