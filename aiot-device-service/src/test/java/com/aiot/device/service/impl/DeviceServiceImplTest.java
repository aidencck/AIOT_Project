package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.dto.home.HomeRoomRelationCheckResp;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.DeviceCredential;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceCredentialRepository;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.security.HomePermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceServiceImplTest {

    private DeviceRepository deviceRepository;
    private ProductRepository productRepository;
    private DeviceCredentialRepository credentialRepository;
    private HomePermissionService homePermissionService;
    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        productRepository = mock(ProductRepository.class);
        credentialRepository = mock(DeviceCredentialRepository.class);
        homePermissionService = mock(HomePermissionService.class);

        deviceService = new DeviceServiceImpl();
        ReflectionTestUtils.setField(deviceService, "deviceRepository", deviceRepository);
        ReflectionTestUtils.setField(deviceService, "productRepository", productRepository);
        ReflectionTestUtils.setField(deviceService, "credentialRepository", credentialRepository);
        ReflectionTestUtils.setField(deviceService, "homePermissionService", homePermissionService);
    }

    @Test
    void createDevice_shouldRejectGatewayFromAnotherHome() {
        DeviceReq req = new DeviceReq();
        req.setDeviceName("sensor-1");
        req.setProductKey("pk-sub");
        req.setHomeId("home-1");
        req.setGatewayId("gw-1");

        when(productRepository.selectOne(any()))
                .thenReturn(product("pk-sub", 3))
                .thenReturn(product("pk-gw", 2));
        when(deviceRepository.selectList(any())).thenReturn(List.of());
        when(homePermissionService.checkHomeRoomRelation("home-1", null)).thenReturn(validRelation());

        Device gateway = new Device();
        gateway.setId("gw-1");
        gateway.setProductKey("pk-gw");
        gateway.setHomeId("home-2");
        when(deviceRepository.selectById("gw-1")).thenReturn(gateway);

        BusinessException ex = assertThrows(BusinessException.class, () -> deviceService.createDevice(req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("网关设备不属于当前家庭", ex.getMessage());
        verify(deviceRepository, never()).insert(any(Device.class));
    }

    @Test
    void createDevice_shouldRejectDuplicateDeviceInSameHome() {
        DeviceReq req = new DeviceReq();
        req.setDeviceName("sensor-1");
        req.setProductKey("pk-1");
        req.setHomeId("home-1");

        when(productRepository.selectOne(any())).thenReturn(product("pk-1", 1));
        when(homePermissionService.checkHomeRoomRelation("home-1", null)).thenReturn(validRelation());

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setHomeId("home-1");
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        BusinessException ex = assertThrows(BusinessException.class, () -> deviceService.createDevice(req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("设备已存在", ex.getMessage());
    }

    @Test
    void createDevice_shouldClaimUnboundDevice() {
        DeviceReq req = new DeviceReq();
        req.setDeviceName("sensor-1");
        req.setProductKey("pk-1");
        req.setHomeId("home-9");

        when(productRepository.selectOne(any())).thenReturn(product("pk-1", 1));
        when(homePermissionService.checkHomeRoomRelation("home-9", null)).thenReturn(validRelation());

        Device existed = new Device();
        existed.setId("dev-1");
        existed.setGlobalDeviceId("gdev-1");
        existed.setProductKey("pk-1");
        existed.setDeviceName("sensor-1");
        existed.setHomeId(null);
        when(deviceRepository.selectList(any())).thenReturn(List.of(existed));

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-1");
        credential.setDeviceSecret("secret-1");
        when(credentialRepository.selectOne(any())).thenReturn(credential);

        DeviceResp resp = deviceService.createDevice(req);

        assertEquals("dev-1", resp.getId());
        assertEquals("gdev-1", resp.getGlobalDeviceId());
        assertEquals("dev-1", resp.getAuthIdentity());
        assertEquals("home-9", resp.getHomeId());
        assertEquals("secret-1", resp.getDeviceSecret());
        verify(deviceRepository, never()).insert(any(Device.class));
        verify(deviceRepository).updateById(existed);
    }

    @Test
    void updateDevice_shouldRejectRoomOutsideHome() {
        Device device = new Device();
        device.setId("dev-1");
        device.setProductKey("pk-1");
        device.setHomeId("home-1");
        when(deviceRepository.selectById("dev-1")).thenReturn(device);
        when(productRepository.selectOne(any())).thenReturn(product("pk-1", 1));

        HomeRoomRelationCheckResp relation = new HomeRoomRelationCheckResp();
        relation.setHomeExists(true);
        relation.setRoomExists(true);
        relation.setRoomBelongsToHome(false);
        when(homePermissionService.checkHomeRoomRelation("home-1", "room-9")).thenReturn(relation);

        DeviceUpdateReq req = new DeviceUpdateReq();
        req.setRoomId("room-9");

        BusinessException ex = assertThrows(BusinessException.class, () -> deviceService.updateDevice("dev-1", req));
        assertEquals(ResultCode.VALIDATE_FAILED, ex.getResultCode());
        assertEquals("房间不属于当前家庭", ex.getMessage());
    }

    private Product product(String productKey, int nodeType) {
        Product product = new Product();
        product.setProductKey(productKey);
        product.setNodeType(nodeType);
        return product;
    }

    private HomeRoomRelationCheckResp validRelation() {
        HomeRoomRelationCheckResp resp = new HomeRoomRelationCheckResp();
        resp.setHomeExists(true);
        resp.setRoomExists(true);
        resp.setRoomBelongsToHome(true);
        return resp;
    }
}
