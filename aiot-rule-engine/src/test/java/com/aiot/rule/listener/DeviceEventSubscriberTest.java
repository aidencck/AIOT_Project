package com.aiot.rule.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.rule.service.RuleLifecycleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceEventSubscriberTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private RuleLifecycleService ruleLifecycleService;
    private DeviceEventSubscriber subscriber;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        ruleLifecycleService = mock(RuleLifecycleService.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        subscriber = new DeviceEventSubscriber(
                objectMapper,
                ruleLifecycleService,
                stringRedisTemplate,
                new SimpleMeterRegistry(),
                "aiot:stream:device-event",
                "aiot:stream:device-event:dlq",
                2,
                0,
                "aiot-rule-engine-group"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldExecuteRuleAndAckOnSuccess() throws Exception {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        DeviceEvent event = DeviceEvent.builder()
                .eventId("evt-1")
                .eventType(DeviceEventType.DEVICE_OFFLINE)
                .deviceId("d-1")
                .timestamp(System.currentTimeMillis())
                .traceId("trace-1")
                .build();
        when(record.getValue()).thenReturn(Map.of("payload", objectMapper.writeValueAsString(event)));

        subscriber.onMessage(record);

        verify(ruleLifecycleService, times(1)).executeByEvent(any(DeviceEvent.class));
        verify(streamOperations).acknowledge("aiot:stream:device-event", "aiot-rule-engine-group", RecordId.of("1-0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldPublishDlqAndAckWhenHandlerStillFails() throws Exception {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("2-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        DeviceEvent event = DeviceEvent.builder()
                .eventId("evt-2")
                .eventType(DeviceEventType.DEVICE_OFFLINE)
                .deviceId("d-2")
                .timestamp(System.currentTimeMillis())
                .build();
        when(record.getValue()).thenReturn(Map.of("payload", objectMapper.writeValueAsString(event)));
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("99-0"));
        org.mockito.Mockito.doThrow(new RuntimeException("rule failed"))
                .when(ruleLifecycleService).executeByEvent(any(DeviceEvent.class));

        subscriber.onMessage(record);

        verify(ruleLifecycleService, times(2)).executeByEvent(any(DeviceEvent.class));
        verify(streamOperations, times(1)).add(any(MapRecord.class));
        verify(streamOperations).acknowledge("aiot:stream:device-event", "aiot-rule-engine-group", RecordId.of("2-0"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldKeepPendingWhenDlqPublishFailed() {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("3-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        when(record.getValue()).thenReturn(Map.of("payload", "{invalid-json"));
        when(streamOperations.add(any(MapRecord.class))).thenThrow(new RuntimeException("dlq down"));

        assertThrows(RuntimeException.class, () -> subscriber.onMessage(record));

        verify(streamOperations, never()).acknowledge(eq("aiot:stream:device-event"), eq("aiot-rule-engine-group"), any(RecordId.class));
    }
}
