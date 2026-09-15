package com.aiot.rule.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterQueueServiceTest {

    private static final String DLQ_STREAM = "aiot:stream:device-event:dlq";
    private static final String MAIN_STREAM = "aiot:stream:device-event";

    private StringRedisTemplate stringRedisTemplate;
    private StreamOperations<String, Object, Object> streamOperations;
    private DeadLetterQueueService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        streamOperations = mock(StreamOperations.class);
        when(stringRedisTemplate.opsForStream()).thenReturn(streamOperations);
        service = new DeadLetterQueueService(stringRedisTemplate, DLQ_STREAM, MAIN_STREAM);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDeadLetters_shouldDeserializeRecentRecords() {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getId()).thenReturn(RecordId.of("2-0"));
        when(record.getValue()).thenReturn(Map.of("payload", "{\"a\":1}", "sourceStream", MAIN_STREAM));
        when(streamOperations.reverseRange(eq(DLQ_STREAM), any(), any()))
                .thenReturn(List.of(record));

        List<Map<String, Object>> result = service.listDeadLetters(10);

        assertEquals(1, result.size());
        assertEquals("2-0", result.get(0).get("recordId"));
        assertEquals("{\"a\":1}", result.get(0).get("payload"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listDeadLetters_shouldReturnEmptyWhenNoRecords() {
        when(streamOperations.reverseRange(eq(DLQ_STREAM), any(), any())).thenReturn(List.of());

        List<Map<String, Object>> result = service.listDeadLetters(10);

        assertEquals(0, result.size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replay_shouldReadPayloadAndAddBackToMainStream() {
        MapRecord<String, Object, Object> record = mock(MapRecord.class);
        when(record.getValue()).thenReturn(Map.of("payload", "{\"eventId\":\"evt-1\"}"));
        when(streamOperations.range(eq(DLQ_STREAM), eq(Range.closed("5-0", "5-0"))))
                .thenReturn(List.of(record));
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("100-0"));

        String replayedId = service.replay("5-0");

        assertEquals("100-0", replayedId);
        verify(streamOperations).add(any(MapRecord.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void replay_shouldFailWhenRecordMissing() {
        when(streamOperations.range(eq(DLQ_STREAM), any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.replay("5-0"));
    }

    @Test
    void deadLetterCount_shouldReturnStreamSize() {
        when(streamOperations.size(DLQ_STREAM)).thenReturn(42L);

        assertEquals(42L, service.deadLetterCount());
    }
}
