package com.aiot.rule.listener;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceEventPendingRecoverySchedulerTest {

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private DeviceEventSubscriber subscriber;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        subscriber = mock(DeviceEventSubscriber.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoverPending_shouldClaimAndReplayWithinMaxDelivery() {
        DeviceEventPendingRecoveryScheduler scheduler = new DeviceEventPendingRecoveryScheduler(
                stringRedisTemplate,
                subscriber,
                new SimpleMeterRegistry(),
                "aiot:stream:device-event",
                "aiot-rule-engine-group",
                "rule-consumer-1",
                true,
                10,
                60000,
                2
        );
        PendingMessages pendingMessages = mock(PendingMessages.class);
        PendingMessage p1 = mock(PendingMessage.class);
        PendingMessage p2 = mock(PendingMessage.class);
        when(p1.getId()).thenReturn(RecordId.of("1-0"));
        when(p1.getTotalDeliveryCount()).thenReturn(1L);
        when(p2.getId()).thenReturn(RecordId.of("2-0"));
        when(p2.getTotalDeliveryCount()).thenReturn(3L);
        when(pendingMessages.isEmpty()).thenReturn(false);
        when(pendingMessages.iterator()).thenReturn(List.of(p1, p2).iterator());
        when(streamOperations.pending("aiot:stream:device-event", "aiot-rule-engine-group", Range.unbounded(), 10L))
                .thenReturn(pendingMessages);

        MapRecord<String, Object, Object> claimedRecord = mock(MapRecord.class);
        when(streamOperations.claim(eq("aiot:stream:device-event"), eq("aiot-rule-engine-group"), eq("rule-consumer-1"),
                eq(Duration.ofMillis(60000)), any(RecordId[].class)))
                .thenReturn(List.of(claimedRecord));

        scheduler.recoverPending();

        verify(subscriber, times(1)).onMessage(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recoverPending_shouldContinueWhenSingleReplayFails() {
        DeviceEventPendingRecoveryScheduler scheduler = new DeviceEventPendingRecoveryScheduler(
                stringRedisTemplate,
                subscriber,
                new SimpleMeterRegistry(),
                "aiot:stream:device-event",
                "aiot-rule-engine-group",
                "rule-consumer-1",
                true,
                10,
                60000,
                10
        );
        PendingMessages pendingMessages = mock(PendingMessages.class);
        PendingMessage pending = mock(PendingMessage.class);
        when(pending.getId()).thenReturn(RecordId.of("1-0"));
        when(pending.getTotalDeliveryCount()).thenReturn(1L);
        when(pendingMessages.isEmpty()).thenReturn(false);
        when(pendingMessages.iterator()).thenReturn(List.of(pending).iterator());
        when(streamOperations.pending("aiot:stream:device-event", "aiot-rule-engine-group", Range.unbounded(), 10L))
                .thenReturn(pendingMessages);

        MapRecord<String, Object, Object> record1 = mock(MapRecord.class);
        MapRecord<String, Object, Object> record2 = mock(MapRecord.class);
        when(streamOperations.claim(any(), any(), any(), any(Duration.class), any(RecordId[].class)))
                .thenReturn(List.of(record1, record2));
        doThrow(new RuntimeException("consume failed")).when(subscriber).onMessage(any(MapRecord.class));

        scheduler.recoverPending();

        verify(subscriber, times(2)).onMessage(any(MapRecord.class));
    }
}
