package com.aiot.rule.service;

import com.aiot.rule.listener.DeviceEventPendingRecoveryScheduler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamBacklogMigrationGateTest {

    private static final String STREAM = "aiot:stream:device-event";
    private static final String GROUP = "aiot-rule-engine-group";

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private DeviceEventPendingRecoveryScheduler recoveryScheduler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        recoveryScheduler = mock(DeviceEventPendingRecoveryScheduler.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
    }

    private StreamBacklogMigrationGate gate(boolean enabled, long threshold) {
        return new StreamBacklogMigrationGate(
                stringRedisTemplate,
                recoveryScheduler,
                new SimpleMeterRegistry(),
                STREAM,
                GROUP,
                enabled,
                threshold
        );
    }

    @Test
    void shouldTriggerMigrationWhenBacklogExceedsThreshold() {
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(summary.getTotalPendingMessages()).thenReturn(15000L);
        when(streamOperations.pending(STREAM, GROUP)).thenReturn(summary);

        gate(true, 10000L).checkAndMigrate();

        verify(recoveryScheduler).recoverPending();
    }

    @Test
    void shouldNotTriggerMigrationWhenBacklogBelowThreshold() {
        PendingMessagesSummary summary = mock(PendingMessagesSummary.class);
        when(summary.getTotalPendingMessages()).thenReturn(9999L);
        when(streamOperations.pending(STREAM, GROUP)).thenReturn(summary);

        gate(true, 10000L).checkAndMigrate();

        verify(recoveryScheduler, never()).recoverPending();
    }

    @Test
    void shouldSkipWhenDisabled() {
        gate(false, 1L).checkAndMigrate();

        verify(recoveryScheduler, never()).recoverPending();
        verify(streamOperations, never()).pending(STREAM, GROUP);
    }
}
