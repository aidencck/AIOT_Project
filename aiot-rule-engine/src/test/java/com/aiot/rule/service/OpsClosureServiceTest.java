package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.rule.client.AiContextProvider;
import com.aiot.rule.client.DeviceStatusSyncClient;
import com.aiot.rule.client.DeviceStatusSummaryClient;
import com.aiot.rule.model.AlarmRecord;
import com.aiot.rule.model.RuleDefinition;
import com.aiot.rule.model.WorkOrderRecord;
import com.aiot.rule.repository.OpsRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsClosureServiceTest {

    private OpsRecordRepository opsRecordRepository;
    private AiContextProvider aiContextProvider;
    private OpsClosureService opsClosureService;

    @BeforeEach
    void setUp() {
        opsRecordRepository = mock(OpsRecordRepository.class);
        aiContextProvider = mock(AiContextProvider.class);
        DeviceIdentityCompatService deviceIdentityCompatService = new DeviceIdentityCompatService(aiContextProvider);
        opsClosureService = new OpsClosureService(
                opsRecordRepository,
                new ObjectMapper(),
                mock(AiPersistenceStatusService.class),
                deviceIdentityCompatService,
                mock(DeviceStatusSummaryClient.class),
                mock(DeviceStatusSyncClient.class)
        );
    }

    @Test
    void shouldPersistResolvedIdentitiesWhenCreatingAlarmAndWorkOrder() {
        when(aiContextProvider.getRuntimeContext("legacy-device-1", "OFFLINE_FLAP"))
                .thenReturn(runtimeContext("g-device-1", "auth-device-1", "sn-device-1"));

        RuleDefinition rule = RuleDefinition.builder()
                .ruleId("rule-1")
                .build();
        DeviceEvent event = DeviceEvent.builder()
                .eventId("event-1")
                .eventType(DeviceEventType.DEVICE_OFFLINE)
                .deviceId("legacy-device-1")
                .timestamp(123456789L)
                .build();

        opsClosureService.createAlarmAndWorkOrder(rule, event, "{\"level\":\"P1\",\"autoCreateWorkOrder\":true}");

        ArgumentCaptor<AlarmRecord> alarmCaptor = ArgumentCaptor.forClass(AlarmRecord.class);
        verify(opsRecordRepository).saveAlarm(alarmCaptor.capture());
        AlarmRecord alarm = alarmCaptor.getValue();
        assertEquals("legacy-device-1", alarm.getDeviceId());
        assertEquals("g-device-1", alarm.getGlobalDeviceId());
        assertEquals("auth-device-1", alarm.getAuthIdentity());
        assertEquals("sn-device-1", alarm.getDeviceSn());

        ArgumentCaptor<WorkOrderRecord> workOrderCaptor = ArgumentCaptor.forClass(WorkOrderRecord.class);
        verify(opsRecordRepository).saveWorkOrder(workOrderCaptor.capture());
        WorkOrderRecord workOrder = workOrderCaptor.getValue();
        assertEquals("legacy-device-1", workOrder.getDeviceId());
        assertEquals("g-device-1", workOrder.getGlobalDeviceId());
        assertEquals("auth-device-1", workOrder.getAuthIdentity());
        assertEquals("sn-device-1", workOrder.getDeviceSn());
        assertEquals("P1", workOrder.getPriority());
    }

    @Test
    void shouldBackfillAlarmIdentityAndFilterByNewIdentifier() {
        when(aiContextProvider.getRuntimeContext("legacy-device-2", "OFFLINE_FLAP"))
                .thenReturn(runtimeContext("g-device-2", "auth-device-2", "sn-device-2"));
        when(opsRecordRepository.findAllAlarms()).thenReturn(List.of(AlarmRecord.builder()
                .alarmId("alarm-1")
                .deviceId("legacy-device-2")
                .status("NEW")
                .createdAt(111L)
                .build()));

        List<AlarmRecord> alarms = opsClosureService.listAlarms("NEW", "auth-device-2");

        assertEquals(1, alarms.size());
        assertEquals("g-device-2", alarms.get(0).getGlobalDeviceId());
        assertEquals("auth-device-2", alarms.get(0).getAuthIdentity());
        assertEquals("sn-device-2", alarms.get(0).getDeviceSn());
        verify(opsRecordRepository).saveAlarm(any(AlarmRecord.class));
    }

    @Test
    void shouldBackfillWorkOrderIdentityAndFilterByDeviceSn() {
        when(aiContextProvider.getRuntimeContext("legacy-device-3", "OFFLINE_FLAP"))
                .thenReturn(runtimeContext("g-device-3", "auth-device-3", "sn-device-3"));
        when(opsRecordRepository.findAllWorkOrders()).thenReturn(List.of(WorkOrderRecord.builder()
                .workOrderId("wo-1")
                .deviceId("legacy-device-3")
                .status("OPEN")
                .assignee("ops-admin")
                .createdAt(222L)
                .slaBreached(Boolean.FALSE)
                .build()));

        List<WorkOrderRecord> workOrders = opsClosureService.listWorkOrders("OPEN", "ops-admin", "sn-device-3");

        assertEquals(1, workOrders.size());
        assertEquals("g-device-3", workOrders.get(0).getGlobalDeviceId());
        assertEquals("auth-device-3", workOrders.get(0).getAuthIdentity());
        assertEquals("sn-device-3", workOrders.get(0).getDeviceSn());
        verify(opsRecordRepository).saveWorkOrder(any(WorkOrderRecord.class));
    }

    @Test
    void shouldNotPersistBackfillWhenIdentityAlreadyComplete() {
        when(opsRecordRepository.findAllAlarms()).thenReturn(List.of(AlarmRecord.builder()
                .alarmId("alarm-2")
                .deviceId("legacy-device-4")
                .globalDeviceId("g-device-4")
                .authIdentity("auth-device-4")
                .deviceSn("sn-device-4")
                .status("NEW")
                .createdAt(333L)
                .build()));

        List<AlarmRecord> alarms = opsClosureService.listAlarms("NEW", "g-device-4");

        assertEquals(1, alarms.size());
        verify(opsRecordRepository, times(0)).saveAlarm(any(AlarmRecord.class));
        verify(aiContextProvider, times(0)).getRuntimeContext(any(), any());
        assertFalse(alarms.isEmpty());
        assertTrue(alarms.get(0).getGlobalDeviceId().startsWith("g-device"));
    }

    private AiRuntimeContextPayload runtimeContext(String globalDeviceId, String authIdentity, String deviceSn) {
        return AiRuntimeContextPayload.builder()
                .runtimeContext(AiRuntimeContext.builder()
                        .deviceId(globalDeviceId)
                        .globalDeviceId(globalDeviceId)
                        .authIdentity(authIdentity)
                        .deviceSn(deviceSn)
                        .build())
                .build();
    }
}
