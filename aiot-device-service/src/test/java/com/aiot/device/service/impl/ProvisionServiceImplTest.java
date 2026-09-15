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
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.DeviceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
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
    private ProductRepository productRepository;
    private HomePermissionService homePermissionService;
    private ProvisionServiceImpl provisionService;

    @BeforeEach
    @SuppressWarnings("null")
    void setUp() {
        redisUtils = mock(RedisUtils.class);
        deviceService = mock(DeviceService.class);
        deviceRepository = mock(DeviceRepository.class);
        credentialRepository = mock(DeviceCredentialRepository.class);
        productRepository = mock(ProductRepository.class);
        homePermissionService = mock(HomePermissionService.class);

        provisionService = new ProvisionServiceImpl();
        ReflectionTestUtils.setField(provisionService, "redisUtils", redisUtils);
        ReflectionTestUtils.setField(provisionService, "deviceService", deviceService);
        ReflectionTestUtils.setField(provisionService, "deviceRepository", deviceRepository);
        ReflectionTestUtils.setField(provisionService, "credentialRepository", credentialRepository);
        ReflectionTestUtils.setField(provisionService, "productRepository", productRepository);
        ReflectionTestUtils.setField(provisionService, "homePermissionService", homePermissionService);
        ReflectionTestUtils.setField(provisionService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(provisionService, "mqttHost", "mqtt.local");
        ReflectionTestUtils.setField(provisionService, "mqttPort", 1883);
        ReflectionTestUtils.setField(provisionService, "tokenTtlSeconds", 120L);
        ReflectionTestUtils.setField(provisionService, "exchangeLockSeconds", 8L);
        ReflectionTestUtils.setField(provisionService, "deviceEventStream", "aiot:stream:device-event");

        Product product = new Product();
        product.setProductKey("pk-1");
        when(productRepository.selectOne(any())).thenReturn(product);
    }

    @Test
    void generateProvisionToken_shouldUseConfiguredTtlSeconds() {
        Product product = new Product();
        product.setProductKey("pk-1");
        when(productRepository.selectOne(any())).thenReturn(product);
        when(redisUtils.buildKey(eq("device"), eq("provision"), anyString()))
                .thenAnswer(invocation -> "aiot:device:provision:" + invocation.getArgument(2, String.class));

        String token = provisionService.generateProvisionToken("pk-1", "sn-1", "dn-1", "home-1");

        assertNotNull(token);
        verify(homePermissionService).requireHomePermission("home-1", 2, "无权限为该家庭发放配网令牌");
        verify(redisUtils).set(anyString(), anyString(), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void provisionDevice_shouldReuseCredentialWhenDeviceAlreadyExists() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-1");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-1")).thenReturn("aiot:device:provision:t-1");
        when(redisUtils.getAndDelete("aiot:device:provision:t-1"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-1")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-1");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString(), eq(8L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setHomeId("home-1");
        existed.setProductKey("pk-1");
        existed.setDeviceSn("sn-1");
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
        verify(redisUtils).releaseIfHeld(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString());
        verify(redisUtils, times(1)).addToStream(eq("aiot:stream:device-event"),
                argThat(fields -> "DEVICE_PROVISION_SUCCEEDED".equals(fields.get("eventType"))));
    }

    @Test
    void provisionDevice_shouldRejectClaimWhenExistingDeviceBoundToAnotherHome() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-1");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-1")).thenReturn("aiot:device:provision:t-1");
        when(redisUtils.getAndDelete("aiot:device:provision:t-1"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-1")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-1");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setHomeId("home-2");
        existed.setProductKey("pk-1");
        existed.setDeviceSn("sn-1");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        BusinessException ex = assertThrows(BusinessException.class, () -> provisionService.provisionDevice(req));
        assertEquals(ResultCode.FORBIDDEN, ex.getResultCode());
        assertEquals("设备已绑定其他家庭，禁止重复认领", ex.getMessage());
        verify(redisUtils).releaseIfHeld(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString());
        verify(redisUtils, times(1)).addToStream(eq("aiot:stream:device-event"),
                argThat(fields -> "DEVICE_PROVISION_REJECTED".equals(fields.get("eventType"))));
    }

    @Test
    void provisionDevice_shouldCreateDeviceWhenNotExists() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-2");
        req.setProductKey("pk-2");
        req.setDeviceName("dn-2");
        req.setGlobalDeviceId("gdev-2");
        req.setDeviceSn("sn-2");
        req.setAuthIdentity("auth-2");

        when(redisUtils.buildKey("device", "provision", "t-2")).thenReturn("aiot:device:provision:t-2");
        when(redisUtils.getAndDelete("aiot:device:provision:t-2"))
                .thenReturn("{\"homeId\":\"home-2\",\"productKey\":\"pk-2\",\"deviceSn\":\"sn-2\",\"deviceName\":\"dn-2\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-2:sn:sn-2")).thenReturn("aiot:device:provision-lock:pk-2:sn:sn-2");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-2:sn:sn-2"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceRepository.selectList(any())).thenReturn(List.of());

        DeviceResp created = new DeviceResp();
        created.setId("dev-2");
        created.setDeviceSecret("secret-2");
        when(deviceService.createDevice(any(DeviceReq.class))).thenReturn(created);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("dev-2", resp.getDeviceId());
        assertEquals("secret-2", resp.getDeviceSecret());
        ArgumentCaptor<DeviceReq> captor = ArgumentCaptor.forClass(DeviceReq.class);
        verify(deviceService).createDevice(captor.capture());
        assertEquals("gdev-2", captor.getValue().getGlobalDeviceId());
        assertEquals("sn-2", captor.getValue().getDeviceSn());
        assertEquals("auth-2", captor.getValue().getAuthIdentity());
        verify(redisUtils).releaseIfHeld(eq("aiot:device:provision-lock:pk-2:sn:sn-2"), anyString());
    }

    @Test
    void provisionDevice_shouldClaimUnboundDevice() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-claim");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-claim");
        req.setDeviceName("dn-claim");

        when(redisUtils.buildKey("device", "provision", "t-claim")).thenReturn("aiot:device:provision:t-claim");
        when(redisUtils.getAndDelete("aiot:device:provision:t-claim"))
                .thenReturn("{\"homeId\":\"home-8\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-claim\",\"deviceName\":\"dn-claim\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-claim")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-claim");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-claim"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-claim");
        existed.setHomeId(null);
        existed.setProductKey("pk-1");
        existed.setDeviceSn("sn-claim");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        DeviceResp claimed = new DeviceResp();
        claimed.setId("dev-claim");
        claimed.setDeviceSecret("secret-claim");
        when(deviceService.claimUnboundDevice(any(DeviceReq.class))).thenReturn(claimed);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("dev-claim", resp.getDeviceId());
        assertEquals("secret-claim", resp.getDeviceSecret());
        verify(deviceService).claimUnboundDevice(any(DeviceReq.class));
        verify(deviceService, never()).createDevice(any(DeviceReq.class));
    }

    @Test
    void provisionDevice_shouldRejectWhenDuplicateRecordsExist() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-3");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-3")).thenReturn("aiot:device:provision:t-3");
        when(redisUtils.getAndDelete("aiot:device:provision:t-3"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-1")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-1");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed1 = new Device();
        existed1.setId("dev-1");
        existed1.setProductKey("pk-1");
        existed1.setDeviceSn("sn-1");
        Device existed2 = new Device();
        existed2.setId("dev-2");
        existed2.setProductKey("pk-1");
        existed2.setDeviceSn("sn-1");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed1, existed2));

        BusinessException ex = assertThrows(BusinessException.class, () -> provisionService.provisionDevice(req));
        assertEquals(ResultCode.FAILED, ex.getResultCode());
    }

    @Test
    void provisionDevice_shouldRecoverAfterConcurrentDuplicateKey() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-4");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-1");
        req.setDeviceName("dn-1");

        when(redisUtils.buildKey("device", "provision", "t-4")).thenReturn("aiot:device:provision:t-4");
        when(redisUtils.getAndDelete("aiot:device:provision:t-4"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-1\",\"deviceName\":\"dn-1\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-1")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-1");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-1"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceRepository.selectList(any()))
                .thenReturn(List.of())
                .thenReturn(List.of(existingDevice("dev-9", "home-1", "pk-1", "sn-1")));
        when(deviceService.createDevice(any(DeviceReq.class))).thenThrow(new DuplicateKeyException("dup"));

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-9");
        credential.setDeviceSecret("secret-9");
        when(credentialRepository.selectOne(any())).thenReturn(credential);

        ProvisionResp resp = provisionService.provisionDevice(req);
        assertEquals("dev-9", resp.getDeviceId());
        assertEquals("secret-9", resp.getDeviceSecret());
    }

    @Test
    void provisionDevice_shouldBackfillCompatibilityFieldsForExistingDevice() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-compat");
        req.setProductKey("pk-1");
        req.setDeviceSn("sn-compat");
        req.setDeviceName("dn-compat");
        req.setGlobalDeviceId("gdev-compat");
        req.setAuthIdentity("auth-compat");

        when(redisUtils.buildKey("device", "provision", "t-compat")).thenReturn("aiot:device:provision:t-compat");
        when(redisUtils.getAndDelete("aiot:device:provision:t-compat"))
                .thenReturn("{\"homeId\":\"home-1\",\"productKey\":\"pk-1\",\"deviceSn\":\"sn-compat\",\"deviceName\":\"dn-compat\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-1:sn:sn-compat")).thenReturn("aiot:device:provision-lock:pk-1:sn:sn-compat");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-1:sn:sn-compat"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Device existed = new Device();
        existed.setId("dev-compat");
        existed.setHomeId("home-1");
        existed.setProductKey("pk-1");
        existed.setDeviceSn("sn-compat");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-compat");
        credential.setDeviceSecret("secret-compat");
        when(credentialRepository.selectOne(any())).thenReturn(credential);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("gdev-compat", resp.getGlobalDeviceId());
        assertEquals("sn-compat", resp.getDeviceSn());
        assertEquals("auth-compat", resp.getAuthIdentity());
        assertEquals("gdev-compat", existed.getGlobalDeviceId());
        assertEquals("dn-compat", existed.getDeviceName());
        assertEquals("sn-compat", existed.getDeviceSn());
        assertEquals("auth-compat", existed.getAuthIdentity());
        verify(deviceRepository).updateById(existed);
    }

    @Test
    void provisionDevice_shouldCreateDeviceWithDeviceSnWhenDeviceNameMissing() {
        ProvisionReq req = new ProvisionReq();
        req.setProvisionToken("t-sn-only");
        req.setProductKey("pk-3");
        req.setDeviceSn("sn-only");

        when(redisUtils.buildKey("device", "provision", "t-sn-only")).thenReturn("aiot:device:provision:t-sn-only");
        when(redisUtils.getAndDelete("aiot:device:provision:t-sn-only"))
                .thenReturn("{\"homeId\":\"home-3\",\"productKey\":\"pk-3\",\"deviceSn\":\"sn-only\"}");
        when(redisUtils.buildKey("device", "provision-lock", "pk-3:sn:sn-only")).thenReturn("aiot:device:provision-lock:pk-3:sn:sn-only");
        when(redisUtils.setIfAbsentString(eq("aiot:device:provision-lock:pk-3:sn:sn-only"), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);
        when(deviceRepository.selectList(any())).thenReturn(List.of());

        DeviceResp created = new DeviceResp();
        created.setId("dev-sn");
        created.setDeviceSecret("secret-sn");
        created.setDeviceSn("sn-only");
        when(deviceService.createDevice(any(DeviceReq.class))).thenReturn(created);

        ProvisionResp resp = provisionService.provisionDevice(req);

        assertEquals("dev-sn", resp.getDeviceId());
        ArgumentCaptor<DeviceReq> captor = ArgumentCaptor.forClass(DeviceReq.class);
        verify(deviceService).createDevice(captor.capture());
        assertEquals("sn-only", captor.getValue().getDeviceSn());
        assertEquals("sn-only", captor.getValue().getDeviceName());
    }

    private Device existingDevice(String deviceId, String homeId, String productKey, String deviceSn) {
        Device device = new Device();
        device.setId(deviceId);
        device.setHomeId(homeId);
        device.setProductKey(productKey);
        device.setDeviceSn(deviceSn);
        return device;
    }
}
