package com.aiot.shadow.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
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
    private HashOperations<String, Object, Object> hashOperations;
    private DeviceEventSubscriber subscriber;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        hashOperations = mock(HashOperations.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);
        subscriber = new DeviceEventSubscriber(
                objectMapper,
                stringRedisTemplate,
                new SimpleMeterRegistry(),
                "aiot:stream:device-event",
                "aiot:stream:device-event:dlq",
                2,
                0,
                "aiot-shadow-service-group"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldAckAfterSuccessfulConsume() throws Exception {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        DeviceEvent event = DeviceEvent.builder()
                .eventId("evt-1")
                .eventType(DeviceEventType.SHADOW_REPORTED_UPDATED)
                .deviceId("d-1")
                .timestamp(System.currentTimeMillis())
                .version(1L)
                .build();
        when(record.getValue()).thenReturn(Map.of("payload", objectMapper.writeValueAsString(event)));

        subscriber.onMessage(record);

        verify(streamOperations).acknowledge("aiot:stream:device-event", "aiot-shadow-service-group", RecordId.of("1-0"));
        verify(streamOperations, never()).add(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldRecordShadowAuditForShadowUpdateEvent() throws Exception {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("1-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        DeviceEvent event = DeviceEvent.builder()
                .eventId("evt-audit")
                .eventType(DeviceEventType.SHADOW_DESIRED_UPDATED)
                .deviceId("d-audit")
                .timestamp(1700000000000L)
                .version(3L)
                .payload(Map.of("desired", "on"))
                .build();
        when(record.getValue()).thenReturn(Map.of("payload", objectMapper.writeValueAsString(event)));

        subscriber.onMessage(record);

        verify(hashOperations).put(eq("aiot:shadow:audit:d-audit"), eq("SHADOW_DESIRED_UPDATED:evt-audit"), any());
        verify(stringRedisTemplate).expire(eq("aiot:shadow:audit:d-audit"), eq(Duration.ofDays(7)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void onMessage_shouldPublishDlqAndAckWhenPayloadInvalid() {
        MapRecord<String, String, String> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("2-0"));
        when(record.getStream()).thenReturn("aiot:stream:device-event");
        when(record.getValue()).thenReturn(Map.of("payload", "{invalid-json"));
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("99-0"));

        subscriber.onMessage(record);

        verify(streamOperations, times(1)).add(any(MapRecord.class));
        verify(streamOperations).acknowledge("aiot:stream:device-event", "aiot-shadow-service-group", RecordId.of("2-0"));
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

        verify(streamOperations, never()).acknowledge(eq("aiot:stream:device-event"), eq("aiot-shadow-service-group"), any(RecordId.class));
    }
}
