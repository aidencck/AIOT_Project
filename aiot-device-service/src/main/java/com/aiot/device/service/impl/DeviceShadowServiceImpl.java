package com.aiot.device.service.impl;

import com.aiot.common.config.RedisUtils;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.device.entity.Device;
import com.aiot.device.service.DeviceShadowService;
import com.aiot.device.support.DeviceIdentityResolver;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DeviceShadowServiceImpl implements DeviceShadowService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeviceIdentityResolver deviceIdentityResolver;

    @Value("${aiot.events.device-status-stream:aiot:stream:device-event}")
    private String deviceEventStream;

    private static final DefaultRedisScript<String> SHADOW_UPDATE_SCRIPT = new DefaultRedisScript<>(
            """
            local expectedVersion = ARGV[1]
            local updatedType = ARGV[2]
            local updatedAt = ARGV[3]
            local fieldCount = tonumber(ARGV[4])
            local currentVersion = redis.call('HGET', KEYS[2], 'version')
            if expectedVersion ~= '' and expectedVersion ~= nil then
                if currentVersion == false then
                    return '0|'
                end
                if tostring(currentVersion) ~= expectedVersion then
                    return '0|' .. tostring(currentVersion)
                end
            end
            local index = 5
            for i = 1, fieldCount do
                redis.call('HSET', KEYS[1], ARGV[index], ARGV[index + 1])
                index = index + 2
            end
            local newVersion = redis.call('INCR', KEYS[3])
            redis.call('HSET', KEYS[2], 'version', newVersion, 'updatedAt', updatedAt, 'lastUpdatedType', updatedType)
            return '1|' .. tostring(newVersion)
            """,
            String.class
    );

    private static final String SHADOW_REPORTED = "reported";
    private static final String SHADOW_DESIRED = "desired";
    private static final String SHADOW_META = "meta";
    private static final Pattern SHADOW_FIELD_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final int MAX_SHADOW_FIELDS = 128;

    @Override
    public void updateReportedShadow(String deviceId, Map<String, Object> reported, Long expectedVersion) {
        validateShadowPayload(reported, "reported");
        Device device = requireDevice(deviceId);
        String shadowDeviceId = resolveShadowDeviceId(device);
        Long version = applyShadowUpdateLua(shadowDeviceId, SHADOW_REPORTED, reported, expectedVersion);
        publishShadowEvent(shadowDeviceId, DeviceEventType.SHADOW_REPORTED_UPDATED, version, reported);
    }

    @Override
    public void updateDesiredShadow(String deviceId, Map<String, Object> desired, Long expectedVersion) {
        validateShadowPayload(desired, "desired");
        Device device = requireDevice(deviceId);
        String shadowDeviceId = resolveShadowDeviceId(device);
        Long version = applyShadowUpdateLua(shadowDeviceId, SHADOW_DESIRED, desired, expectedVersion);
        publishShadowEvent(shadowDeviceId, DeviceEventType.SHADOW_DESIRED_UPDATED, version, desired);
    }

    @Override
    public Map<String, Object> getDeviceShadow(String deviceId) {
        Device device = requireDevice(deviceId);
        String shadowDeviceId = resolveShadowDeviceId(device);

        Map<Object, Object> reported = loadShadowEntries("shadow:reported", shadowDeviceId, device.getId());
        Map<Object, Object> desired = loadShadowEntries("shadow:desired", shadowDeviceId, device.getId());
        Map<Object, Object> meta = loadRawEntries("shadow:meta", shadowDeviceId, device.getId());

        Map<String, Object> shadow = new HashMap<>();
        shadow.put(SHADOW_REPORTED, reported);
        shadow.put(SHADOW_DESIRED, desired);
        shadow.put("delta", computeDesiredDelta(desired, reported));
        shadow.put(SHADOW_META, meta);
        return shadow;
    }

    private void validateShadowPayload(Map<String, Object> payload, String type) {
        if (payload == null || payload.isEmpty()) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备影子 " + type + " 不能为空");
        }
        if (payload.size() > MAX_SHADOW_FIELDS) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备影子字段数量超过上限");
        }
        for (String field : payload.keySet()) {
            if (!StringUtils.hasText(field) || !SHADOW_FIELD_PATTERN.matcher(field).matches()) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "影子字段名非法: " + field);
            }
        }
    }

    private Long applyShadowUpdateLua(String deviceId, String type, Map<String, Object> payload, Long expectedVersion) {
        String shadowKey = redisUtils.buildKey("device", "shadow:" + type, deviceId);
        String metaKey = redisUtils.buildKey("device", "shadow:meta", deviceId);
        String versionKey = redisUtils.buildKey("device", "shadow:version", deviceId);
        List<String> keys = List.of(shadowKey, metaKey, versionKey);
        List<Object> args = new ArrayList<>();
        args.add(expectedVersion == null ? "" : String.valueOf(expectedVersion));
        args.add(type);
        args.add(String.valueOf(System.currentTimeMillis()));
        args.add(String.valueOf(payload.size()));
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            args.add(entry.getKey());
            args.add(toJsonString(entry.getValue()));
        }
        String result = redisTemplate.execute(
                SHADOW_UPDATE_SCRIPT,
                redisTemplate.getStringSerializer(),
                redisTemplate.getStringSerializer(),
                keys,
                args.toArray()
        );
        if (!StringUtils.hasText(result) || !result.contains("|")) {
            throw new BusinessException(ResultCode.FAILED, "设备影子更新失败");
        }
        String[] split = result.split("\\|", 2);
        String ok = split[0];
        if (!"1".equals(ok)) {
            throw new BusinessException(ResultCode.SHADOW_VERSION_CONFLICT, "设备影子版本冲突");
        }
        return Long.parseLong(split[1]);
    }

    private Map<Object, Object> normalizeShadowState(Map<Object, Object> rawState) {
        Map<Object, Object> normalized = new HashMap<>();
        for (Map.Entry<Object, Object> entry : rawState.entrySet()) {
            normalized.put(entry.getKey(), fromJsonString(entry.getValue()));
        }
        return normalized;
    }

    private String toJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private Object fromJsonString(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        try {
            return objectMapper.readValue(text, Object.class);
        } catch (Exception ex) {
            return text;
        }
    }

    private Map<String, Object> computeDesiredDelta(Map<Object, Object> desired, Map<Object, Object> reported) {
        Map<String, Object> delta = new HashMap<>();
        for (Map.Entry<Object, Object> entry : desired.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object desiredValue = entry.getValue();
            Object reportedValue = reported.get(key);
            if (!ObjectUtils.nullSafeEquals(desiredValue, reportedValue)) {
                delta.put(key, desiredValue);
            }
        }
        return delta;
    }

    private void publishShadowEvent(String deviceId, DeviceEventType eventType, Long version, Map<String, Object> delta) {
        if (delta.isEmpty()) {
            return;
        }
        DeviceEvent event = DeviceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .deviceId(deviceId)
                .timestamp(System.currentTimeMillis())
                .source("aiot-device-service")
                .traceId(MDC.get("traceId"))
                .version(version)
                .payload(delta)
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", event.getEventId());
            fields.put("eventType", eventType.name());
            fields.put("deviceId", deviceId);
            fields.put("payload", payload);
            redisUtils.addToStream(deviceEventStream, fields);
        } catch (JsonProcessingException ignored) {
            // ignore publish serialization failure to avoid breaking core shadow write path
        }
    }

    private Device requireDevice(String deviceIdentity) {
        return deviceIdentityResolver.requireByIdentity(deviceIdentity, "设备不存在");
    }

    private String resolveShadowDeviceId(Device device) {
        return deviceIdentityResolver.resolveGlobalDeviceId(device);
    }

    private Map<Object, Object> loadShadowEntries(String namespace, String canonicalDeviceId, String legacyDeviceId) {
        return normalizeShadowState(loadRawEntries(namespace, canonicalDeviceId, legacyDeviceId));
    }

    private Map<Object, Object> loadRawEntries(String namespace, String canonicalDeviceId, String legacyDeviceId) {
        String canonicalKey = redisUtils.buildKey("device", namespace, canonicalDeviceId);
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(canonicalKey);
        if (!entries.isEmpty() || !StringUtils.hasText(legacyDeviceId) || canonicalDeviceId.equals(legacyDeviceId)) {
            return entries;
        }
        String legacyKey = redisUtils.buildKey("device", namespace, legacyDeviceId);
        return stringRedisTemplate.opsForHash().entries(legacyKey);
    }
}
