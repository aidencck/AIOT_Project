package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.DeviceCredential;
import com.aiot.device.repository.DeviceCredentialRepository;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProvisionServiceImplTest {

    private RedisUtils redisUtils;
    private DeviceService deviceService;
    private DeviceRepository deviceRepository;
    private DeviceCredentialRepository credentialRepository;
    private HomePermissionService homePermissionService;
    private ProvisionServiceImpl provisionService;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        redisUtils = mock(RedisUtils.class);
        deviceService = mock(DeviceService.class);
        deviceRepository = mock(DeviceRepository.class);
        credentialRepository = mock(DeviceCredentialRepository.class);
        homePermissionService = mock(HomePermissionService.class);

        provisionService = new ProvisionServiceImpl();
        ReflectionTestUtils.setField(provisionService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(provisionService, "deviceService", deviceService);
        ReflectionTestUtils.setField(provisionService, "deviceRepository", deviceRepository);
        ReflectionTestUtils.setField(provisionService, "credentialRepository", credentialRepository);
        ReflectionTestUtils.setField(provisionService, "homePermissionService", homePermissionService);
        ReflectionTestUtils.setField(provisionService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(provisionService, "mqttHost", "mqtt.local");
        ReflectionTestUtils.setField(provisionService, "mqttPort", 1883);
        ReflectionTestUtils.setField(provisionService, "tokenTtlSeconds", 120L);
        ReflectionTestUtils.setField(provisionService, "exchangeLockSeconds", 8L);
        ReflectionTestUtils.setField(provisionService, "deviceEventStream", "aiot:stream:device-event");
    }

    @Test
    void generateProvisionToken_shouldUseConfiguredTtlSeconds() {
        when(redisUtils.buildKey(eq("device"), eq("provision"), anyString()))
                .thenAnswer(invocation -> "aiot:device:provision:" + invocation.getArgument(2, String.class));

        String token = provisionService.generateProvisionToken("pk-1", "dn-1", "home-1");

        assertNotNull(token);
        verify(homePermissionService).requireHomePermission("home-1", 2, "无权限为该家庭发放配网令牌");
        verify(redisUtils).set(anyString(), anyString(), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void provisionDevice_shouldReuseCredentialWhenDeviceAlreadyExists() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-1");
        req.setProductKey("pk-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-1")).thenReturn("aiot:device:provision:t-1");
        when(redisUtils.getAndDelete("aiot:device:provision:t-1"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:dn-1")).thenReturn("aiot:device:provision-lock:pk-1:dn-1");
        when(redisUtils.setIfAbsent(eq("aiot:device:provision-lock:pk-1:dn-1"), anyString(), eq(8L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setHomeId("home-1");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-1");
        credential.setDeviceSecret("secret-1");
        when(credentialRepository.selectOne(any())).thenReturn(credential);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("dev-1", resp.getDeviceId());
        assertEquals("secret-1", resp.getDeviceSecret());
        assertEquals("mqtt.local", resp.getMqttHost());
        assertEquals(1883, resp.getMqttPort());
        verify(deviceService, never()).createDevice(any(DeviceReq.class));
        verify(redisUtils).delete("aiot:device:provision-lock:pk-1:dn-1");
        verify(redisUtils, times(1)).addToStream(eq("aiot:stream:device-event"),
                argThat(fields -> "DEVICE_PROVISION_SUCCEEDED".equals(fields.get("eventType"))));
    }

    @Test
    void provisionDevice_shouldRejectClaimWhenExistingDeviceBoundToAnotherHome() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-1");
        req.setProductKey("pk-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-1")).thenReturn("aiot:device:provision:t-1");
        when(redisUtils.getAndDelete("aiot:device:provision:t-1"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:dn-1")).thenReturn("aiot:device:provision-lock:pk-1:dn-1");
        when(redisUtils.setIfAbsent(eq("aiot:device:provision-lock:pk-1:dn-1"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setHomeId("home-2");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        BusinessException ex = assertThrows(BusinessException.class, () -> provisionService.provisionDevice(req));
        assertEquals(ResultCode.FORBIDDEN, ex.getResultCode());
        assertEquals("设备已绑定其他家庭，禁止重复认领", ex.getMessage());
        verify(redisUtils).delete("aiot:device:provision-lock:pk-1:dn-1");
        verify(redisUtils, times(1)).addToStream(eq("aiot:stream:device-event"),
                argThat(fields -> "DEVICE_PROVISION_REJECTED".equals(fields.get("eventType"))));
    }

    @Test
    void provisionDevice_shouldCreateDeviceWhenNotExists() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-2");
        req.setProductKey("pk-2");
        req.setDeviceName("dn-2");

        when(redisUtils.buildKey("device", "provision", "t-2")).thenReturn("aiot:device:provision:t-2");
        when(redisUtils.getAndDelete("aiot:device:provision:t-2"))
                .thenReturn("{\"homeId\":\"home-2\",\"productKey\":\"pk-2\",\"deviceName\":\"dn-2\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-2:dn-2")).thenReturn("aiot:device:provision-lock:pk-2:dn-2");
        when(redisUtils.setIfAbsent(eq("aiot:device:provision-lock:pk-2:dn-2"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceRepository.selectList(any())).thenReturn(List.of());

        DeviceResp created = new DeviceResp();
        created.setId("dev-2");
        created.setDeviceSecret("secret-2");
        when(deviceService.createDevice(any(DeviceReq.class))).thenReturn(created);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("dev-2", resp.getDeviceId());
        assertEquals("secret-2", resp.getDeviceSecret());
        verify(deviceService).createDevice(any(DeviceReq.class));
        verify(redisUtils).delete("aiot:device:provision-lock:pk-2:dn-2");
    }
}
