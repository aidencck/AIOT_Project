package com.aiot.device.service.impl;

import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.device.client.OpsSummaryClient;
import com.aiot.device.entity.Device;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.DeviceEventHistoryService;
import com.aiot.device.service.DeviceShadowService;
import com.aiot.device.support.DeviceIdentityResolver;
import com.aiot.device.support.DeviceModelStandardizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class AiContextFacadeImplTest {

    private DeviceRepository deviceRepository;
    private ProductRepository productRepository;
    private DeviceShadowService deviceShadowService;
    private DeviceEventHistoryService deviceEventHistoryService;
    private DeviceIdentityResolver deviceIdentityResolver;
    private DeviceModelStandardizer deviceModelStandardizer;
    private OpsSummaryClient opsSummaryClient;
    private AiContextFacadeImpl facade;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        productRepository = mock(ProductRepository.class);
        deviceShadowService = mock(DeviceShadowService.class);
        deviceEventHistoryService = mock(DeviceEventHistoryService.class);
        deviceIdentityResolver = new DeviceIdentityResolver(deviceRepository);
        deviceModelStandardizer = mock(DeviceModelStandardizer.class);
        opsSummaryClient = mock(OpsSummaryClient.class);

        facade = new AiContextFacadeImpl(deviceRepository, productRepository, deviceShadowService,
                deviceEventHistoryService, deviceIdentityResolver, deviceModelStandardizer, opsSummaryClient);
        ReflectionTestUtils.setField(facade, "heartbeatStaleSeconds", 300L);

        when(deviceModelStandardizer.standardize(any())).thenReturn("{}");
        when(deviceEventHistoryService.recentEvents(anyString(), eq(20))).thenReturn(List.of());
    }

    @Test
    void buildRuntimeContext_shouldReturnNullWhenDeviceNotFound() {
        when(deviceRepository.selectByIdentity("missing")).thenReturn(null);

        assertThat(facade.buildRuntimeContext("missing", "scene")).isNull();
        assertThat(facade.buildDeviceContext("missing", "scene")).isNull();
    }

    @Test
    void onlineStatus_shouldBeUnknownWhenStatusIsNull() {
        stubDevice(device(null, null));

        assertThat(buildRuntimeContext().getOnlineStatus()).isEqualTo("unknown");
    }

    @Test
    void onlineStatus_shouldBeUnactivatedWhenStatusIsZero() {
        stubDevice(device(0, null));

        assertThat(buildRuntimeContext().getOnlineStatus()).isEqualTo("unactivated");
    }

    @Test
    void onlineStatus_shouldBeOfflineWhenStatusIsTwo() {
        stubDevice(device(2, null));

        assertThat(buildRuntimeContext().getOnlineStatus()).isEqualTo("offline");
    }

    @Test
    void onlineStatus_shouldBeOnlineWhenStatusIsOneAndHeartbeatIsFresh() {
        stubDevice(device(1, LocalDateTime.now().minusSeconds(10)));

        assertThat(buildRuntimeContext().getOnlineStatus()).isEqualTo("online");
    }

    @Test
    void onlineStatus_shouldBeStaleWhenStatusIsOneAndHeartbeatIsStale() {
        stubDevice(device(1, LocalDateTime.now().minusSeconds(400)));

        assertThat(buildRuntimeContext().getOnlineStatus()).isEqualTo("stale");
    }

    @Test
    void shadowSummary_shouldFallbackWhenShadowServiceThrows() {
        stubDevice(device(1, LocalDateTime.now().minusSeconds(10)));
        when(deviceShadowService.getDeviceShadow("gdev-1")).thenThrow(new RuntimeException("redis down"));

        Map<String, Object> shadow = buildRuntimeContext().getShadowSummary();

        assertThat(shadow)
                .containsEntry("shadowAvailable", false)
                .containsEntry("reported", Map.of())
                .containsEntry("desired", Map.of())
                .containsEntry("delta", Map.of())
                .containsEntry("meta", Map.of());
    }

    @Test
    void shadowSummary_shouldMarkAvailableWhenShadowServiceSucceeds() {
        stubDevice(device(1, LocalDateTime.now().minusSeconds(10)));
        Map<String, Object> shadowData = new HashMap<>();
        shadowData.put("reported", Map.of("power", 1));
        when(deviceShadowService.getDeviceShadow("gdev-1")).thenReturn(shadowData);

        Map<String, Object> shadow = buildRuntimeContext().getShadowSummary();

        assertThat(shadow.get("shadowAvailable")).isEqualTo(true);
        assertThat(shadow.get("reported")).isEqualTo(Map.of("power", 1));
    }

    @Test
    void buildDeviceContext_shouldReturnNullWhenRuntimeContextIsNull() {
        AiContextFacadeImpl spyFacade = spy(facade);
        doReturn(AiRuntimeContextPayload.builder().build())
                .when(spyFacade)
                .buildRuntimeContext(anyString(), anyString());

        assertThat(spyFacade.buildDeviceContext("dev-1", "scene")).isNull();
    }

    @Test
    void buildDeviceContext_shouldConvertNullOpsSummaryToEmptyMap() {
        stubDevice(device(1, LocalDateTime.now().minusSeconds(10)));
        when(deviceShadowService.getDeviceShadow("gdev-1")).thenReturn(new HashMap<>());
        when(opsSummaryClient.getDeviceOpsSummary("gdev-1")).thenReturn(null);

        AiDeviceContextResp resp = facade.buildDeviceContext("dev-1", "scene");

        assertThat(resp).isNotNull();
        assertThat(resp.getDeviceId()).isEqualTo("gdev-1");
        assertThat(resp.getOpsSummary()).isEmpty();
    }

    private AiRuntimeContext buildRuntimeContext() {
        return facade.buildRuntimeContext("dev-1", "scene").getRuntimeContext();
    }

    private void stubDevice(Device device) {
        when(deviceRepository.selectByIdentity("dev-1")).thenReturn(device);
        when(deviceShadowService.getDeviceShadow("gdev-1")).thenReturn(new HashMap<>());
    }

    private Device device(Integer status, LocalDateTime lastHeartbeatTime) {
        Device device = new Device();
        device.setId("dev-1");
        device.setGlobalDeviceId("gdev-1");
        device.setProductKey("pk-1");
        device.setDeviceSn("sn-1");
        device.setAuthIdentity("auth-1");
        device.setStatus(status);
        device.setLastHeartbeatTime(lastHeartbeatTime);
        return device;
    }
}
