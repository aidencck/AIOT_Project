package com.aiot.device.service.impl;

import com.aiot.device.service.DeviceEventHistoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceEventHistoryServiceTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private StringRedisTemplate stringRedisTemplate;
    private ListOperations<String, String> listOperations;
    private ObjectMapper objectMapper;
    private DeviceEventHistoryService historyService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = mock(StringRedisTemplate.class);
        listOperations = mock(ListOperations.class);
        objectMapper = new ObjectMapper();
        when(stringRedisTemplate.opsForList()).thenReturn(listOperations);
        historyService = new DeviceEventHistoryServiceImpl(stringRedisTemplate, objectMapper, "aiot:device:event:", 20);
    }

    @Test
    void record_shouldPushJsonAndTrim() throws Exception {
        historyService.record("dev-1", "DEVICE_ONLINE", 1, 1700000000000L);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).leftPush(eq("aiot:device:event:dev-1"), valueCaptor.capture());
        verify(listOperations).trim("aiot:device:event:dev-1", 0, 19);

        Map<String, Object> event = objectMapper.readValue(valueCaptor.getValue(), MAP_TYPE_REFERENCE);
        assertEquals("DEVICE_ONLINE", event.get("eventType"));
        assertEquals(1, event.get("status"));
        assertEquals(1700000000000L, event.get("timestamp"));
    }

    @Test
    void record_shouldSwallowRedisException() {
        when(listOperations.leftPush(anyString(), anyString()))
                .thenThrow(new RuntimeException("redis unavailable"));

        assertDoesNotThrow(() -> historyService.record("dev-1", "DEVICE_ONLINE", 1, 1700000000000L));
    }

    @Test
    void recentEvents_shouldReturnDeserializedEvents() {
        when(listOperations.range("aiot:device:event:dev-1", 0, 19)).thenReturn(List.of(
                "{\"eventType\":\"DEVICE_ONLINE\",\"status\":1,\"timestamp\":1700000000000}",
                "{\"eventType\":\"DEVICE_OFFLINE\",\"status\":2,\"timestamp\":1700000001000}"
        ));

        List<Map<String, Object>> events = historyService.recentEvents("dev-1", 20);

        assertEquals(2, events.size());
        assertEquals("DEVICE_ONLINE", events.get(0).get("eventType"));
        assertEquals(2, events.get(1).get("status"));
        assertEquals(1700000001000L, events.get(1).get("timestamp"));
    }

    @Test
    void recentEvents_shouldReturnEmptyWhenNoData() {
        when(listOperations.range("aiot:device:event:dev-1", 0, 19)).thenReturn(Collections.emptyList());

        assertTrue(historyService.recentEvents("dev-1", 20).isEmpty());
    }

    @Test
    void recentEvents_shouldReturnEmptyWhenDeviceIdBlank() {
        assertTrue(historyService.recentEvents(" ", 20).isEmpty());
    }

    @Test
    void recentEvents_shouldReturnEmptyOnRedisException() {
        when(listOperations.range(anyString(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("redis unavailable"));

        assertTrue(historyService.recentEvents("dev-1", 20).isEmpty());
    }
}
