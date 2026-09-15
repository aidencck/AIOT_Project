package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.entity.Device;
import com.aiot.device.support.DeviceIdentityResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings({"rawtypes", "unchecked"})
class DeviceShadowServiceImplTest {

    private RedisUtils redisUtils;
    private RedisTemplate<String, Object> redisTemplate;
    private StringRedisTemplate stringRedisTemplate;
    private ObjectMapper objectMapper;
    private DeviceIdentityResolver deviceIdentityResolver;
    private DeviceShadowServiceImpl shadowService;

    @BeforeEach
    void setUp() {
        redisUtils = mock(RedisUtils.class);
        redisTemplate = mock(RedisTemplate.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        objectMapper = new ObjectMapper();
        deviceIdentityResolver = mock(DeviceIdentityResolver.class);

        shadowService = new DeviceShadowServiceImpl();
        ReflectionTestUtils.setField(shadowService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(shadowService, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(shadowService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(shadowService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(shadowService, "deviceIdentityResolver", deviceIdentityResolver);
        ReflectionTestUtils.setField(shadowService, "deviceEventStream", "aiot:stream:device-event");

        when(redisUtils.buildKey(anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> String.format("aiot:%s:%s:%s",
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
    }

    @Test
    void updateReportedShadow_shouldRejectEmptyPayload() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> shadowService.updateReportedShadow("dev-1", Map.of(), null));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
        assertThat(ex.getMessage()).isEqualTo("设备影子 reported 不能为空");
    }

    @Test
    void updateReportedShadow_shouldRejectTooManyFields() {
        Map<String, Object> payload = new HashMap<>();
        for (int i = 0; i < 129; i++) {
            payload.put("field" + i, i);
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shadowService.updateReportedShadow("dev-1", payload, null));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
        assertThat(ex.getMessage()).isEqualTo("设备影子字段数量超过上限");
    }

    @Test
    void updateReportedShadow_shouldRejectInvalidFieldName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> shadowService.updateReportedShadow("dev-1", Map.of("bad field!", "x"), null));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.VALIDATE_FAILED);
        assertThat(ex.getMessage()).isEqualTo("影子字段名非法: bad field!");
    }

    @Test
    void updateReportedShadow_shouldThrowVersionConflictWhenLuaReturnsZero() {
        stubDevice();
        stubLuaResult("0|3");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shadowService.updateReportedShadow("dev-1", Map.of("power", "1"), 3L));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.SHADOW_VERSION_CONFLICT);
        assertThat(ex.getMessage()).isEqualTo("设备影子版本冲突");
    }

    @Test
    void updateReportedShadow_shouldPublishEventWithNewVersionWhenLuaSucceeds() throws Exception {
        stubDevice();
        stubLuaResult("1|5");

        shadowService.updateReportedShadow("dev-1", Map.of("power", "1"), null);

        ArgumentCaptor<Map<String, String>> fieldsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(redisUtils).addToStream(eq("aiot:stream:device-event"), fieldsCaptor.capture());

        DeviceEvent event = objectMapper.readValue(fieldsCaptor.getValue().get("payload"), DeviceEvent.class);
        assertThat(event.getVersion()).isEqualTo(5L);
        assertThat(event.getEventType()).isEqualTo(DeviceEventType.SHADOW_REPORTED_UPDATED);
    }

    @Test
    void updateReportedShadow_shouldThrowUpdateFailedWhenLuaReturnsNull() {
        stubDevice();
        stubLuaResult(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> shadowService.updateReportedShadow("dev-1", Map.of("power", "1"), null));

        assertThat(ex.getResultCode()).isEqualTo(ResultCode.FAILED);
        assertThat(ex.getMessage()).isEqualTo("设备影子更新失败");
    }

    @Test
    void getDeviceShadow_shouldComputeDesiredReportedDelta() {
        stubDevice();

        Map<Object, Object> reported = new LinkedHashMap<>();
        reported.put("power", "0");
        reported.put("brightness", "50");
        reported.put("mode", "auto");

        Map<Object, Object> desired = new LinkedHashMap<>();
        desired.put("power", "1");
        desired.put("brightness", "50");

        Map<Object, Object> meta = new LinkedHashMap<>();
        meta.put("version", "5");

        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries("aiot:device:shadow:reported:gdev-1")).thenReturn(reported);
        when(hashOps.entries("aiot:device:shadow:desired:gdev-1")).thenReturn(desired);
        when(hashOps.entries("aiot:device:shadow:meta:gdev-1")).thenReturn(meta);

        Map<String, Object> shadow = shadowService.getDeviceShadow("dev-1");

        Map<Object, Object> reportedResult = (Map<Object, Object>) shadow.get("reported");
        assertThat(reportedResult.get("power")).isEqualTo(0);
        assertThat(reportedResult.get("brightness")).isEqualTo(50);

        Map<Object, Object> desiredResult = (Map<Object, Object>) shadow.get("desired");
        assertThat(desiredResult.get("power")).isEqualTo(1);

        Map<String, Object> delta = (Map<String, Object>) shadow.get("delta");
        assertThat(delta).containsOnlyKeys("power");
        assertThat(delta.get("power")).isEqualTo(1);

        Map<Object, Object> metaResult = (Map<Object, Object>) shadow.get("meta");
        assertThat(metaResult.get("version")).isEqualTo("5");
    }

    private void stubDevice() {
        Device device = new Device();
        device.setId("gdev-1");
        device.setGlobalDeviceId("gdev-1");
        when(deviceIdentityResolver.requireByIdentity("dev-1", "设备不存在")).thenReturn(device);
        when(deviceIdentityResolver.resolveGlobalDeviceId(device)).thenReturn("gdev-1");
    }

    private void stubLuaResult(String result) {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                any(),
                any(),
                anyList(),
                any(Object[].class)))
                .thenReturn(result);
    }
}
