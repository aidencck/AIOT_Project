package com.aiot.device.service.impl;

import com.aiot.device.service.DeviceEventHistoryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DeviceEventHistoryServiceImpl implements DeviceEventHistoryService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE = new TypeReference<>() {
    };

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final int maxSize;

    public DeviceEventHistoryServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${aiot.events.device-history-key-prefix:aiot:device:event:}") String keyPrefix,
            @Value("${aiot.events.device-history-max-size:20}") int maxSize) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.maxSize = maxSize;
    }

    @Override
    public void record(String deviceId, String eventType, Integer status, long epochMillis) {
        try {
            if (!StringUtils.hasText(deviceId)) {
                return;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("eventType", eventType);
            event.put("status", status);
            event.put("timestamp", epochMillis);
            String json = objectMapper.writeValueAsString(event);
            String key = keyPrefix + deviceId;
            stringRedisTemplate.opsForList().leftPush(key, json);
            if (maxSize > 0) {
                stringRedisTemplate.opsForList().trim(key, 0, maxSize - 1);
            }
        } catch (Exception ex) {
            log.warn("Failed to record device event history, deviceId={}, eventType={}", deviceId, eventType, ex);
        }
    }

    @Override
    public List<Map<String, Object>> recentEvents(String deviceId, int limit) {
        try {
            if (!StringUtils.hasText(deviceId) || limit <= 0) {
                return Collections.emptyList();
            }
            List<String> values = stringRedisTemplate.opsForList().range(keyPrefix + deviceId, 0, limit - 1);
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }
            List<Map<String, Object>> result = new ArrayList<>(values.size());
            for (String value : values) {
                result.add(objectMapper.readValue(value, MAP_TYPE_REFERENCE));
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to read device event history, deviceId={}, limit={}", deviceId, limit, ex);
            return Collections.emptyList();
        }
    }
}
