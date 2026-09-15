package com.aiot.device.service.impl;

import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.device.client.OpsSummaryClient;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.ProductRepository;
import com.aiot.device.service.AiContextFacade;
import com.aiot.device.service.DeviceEventHistoryService;
import com.aiot.device.service.DeviceShadowService;
import com.aiot.device.support.DeviceIdentityResolver;
import com.aiot.device.support.DeviceModelStandardizer;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class AiContextFacadeImpl implements AiContextFacade {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DeviceRepository deviceRepository;
    private final ProductRepository productRepository;
    private final DeviceShadowService deviceShadowService;
    private final DeviceEventHistoryService deviceEventHistoryService;
    private final DeviceIdentityResolver deviceIdentityResolver;
    private final DeviceModelStandardizer deviceModelStandardizer;
    private final OpsSummaryClient opsSummaryClient;

    @Value("${aiot.device.heartbeat-stale-seconds:300}")
    private long heartbeatStaleSeconds;

    public AiContextFacadeImpl(DeviceRepository deviceRepository,
                               ProductRepository productRepository,
                               DeviceShadowService deviceShadowService,
                               DeviceEventHistoryService deviceEventHistoryService,
                               DeviceIdentityResolver deviceIdentityResolver,
                               DeviceModelStandardizer deviceModelStandardizer,
                               OpsSummaryClient opsSummaryClient) {
        this.deviceRepository = deviceRepository;
        this.productRepository = productRepository;
        this.deviceShadowService = deviceShadowService;
        this.deviceEventHistoryService = deviceEventHistoryService;
        this.deviceIdentityResolver = deviceIdentityResolver;
        this.deviceModelStandardizer = deviceModelStandardizer;
        this.opsSummaryClient = opsSummaryClient;
    }

    @Override
    public AiDeviceContextResp buildDeviceContext(String deviceId, String sceneType) {
        AiRuntimeContextPayload payload = buildRuntimeContext(deviceId, sceneType);
        if (payload == null || payload.getRuntimeContext() == null) {
            return null;
        }
        AiRuntimeContext context = payload.getRuntimeContext();
        Map<String, Object> opsSummary = opsSummaryClient.getDeviceOpsSummary(context.getDeviceId());
        if (opsSummary == null) {
            opsSummary = Collections.emptyMap();
        }
        return AiDeviceContextResp.builder()
                .sceneType(context.getSceneType())
                .deviceId(context.getDeviceId())
                .globalDeviceId(context.getGlobalDeviceId())
                .authIdentity(context.getAuthIdentity())
                .deviceSn(context.getDeviceSn())
                .deviceName(context.getDeviceName())
                .productKey(context.getProductKey())
                .homeId(context.getHomeId())
                .roomId(context.getRoomId())
                .gatewayId(context.getGatewayId())
                .status(context.getStatus())
                .firmwareVersion(context.getFirmwareVersion())
                .lastHeartbeatTime(context.getLastHeartbeatTime())
                .onlineStatus(context.getOnlineStatus())
                .thingModelJson(payload.getThingModelJson())
                .deviceModelJson(payload.getDeviceModelJson())
                .shadowSummary(context.getShadowSummary())
                .recentEvents(context.getRecentEvents())
                .opsSummary(opsSummary)
                .build();
    }

    @Override
    public AiRuntimeContextPayload buildRuntimeContext(String deviceId, String sceneType) {
        Device device = deviceIdentityResolver.findByIdentity(deviceId);
        if (device == null) {
            return null;
        }
        Product product = findProduct(device.getProductKey());
        String globalDeviceId = deviceIdentityResolver.resolveGlobalDeviceId(device);
        Map<String, Object> shadowSummary = safeShadow(globalDeviceId);
        String onlineStatus = deriveOnlineStatus(device);
        String deviceModelJson = deviceModelStandardizer.standardize(product == null ? null : product.getThingModelJson());
        return AiRuntimeContextPayload.builder()
                .runtimeContext(AiRuntimeContext.builder()
                        .sceneType(sceneType)
                        .deviceId(globalDeviceId)
                        .globalDeviceId(globalDeviceId)
                        .authIdentity(deviceIdentityResolver.resolveAuthIdentity(device))
                        .deviceSn(device.getDeviceSn())
                        .deviceName(device.getDeviceName())
                        .productKey(device.getProductKey())
                        .homeId(device.getHomeId())
                        .roomId(device.getRoomId())
                        .gatewayId(device.getGatewayId())
                        .status(device.getStatus())
                        .firmwareVersion(device.getFirmwareVersion())
                        .lastHeartbeatTime(device.getLastHeartbeatTime() == null
                                ? null
                                : DATETIME_FORMATTER.format(device.getLastHeartbeatTime()))
                        .onlineStatus(onlineStatus)
                        .shadowSummary(shadowSummary)
                        .recentEvents(deviceEventHistoryService.recentEvents(globalDeviceId, 20))
                        .build())
                .thingModelJson(deviceModelJson)
                .deviceModelJson(deviceModelJson)
                .build();
    }

    private Product findProduct(String productKey) {
        if (!StringUtils.hasText(productKey)) {
            return null;
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        return productRepository.selectOne(wrapper);
    }

    private Map<String, Object> safeShadow(String deviceId) {
        try {
            Map<String, Object> shadow = deviceShadowService.getDeviceShadow(deviceId);
            shadow.put("shadowAvailable", true);
            return shadow;
        } catch (Exception ex) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("reported", Collections.emptyMap());
            fallback.put("desired", Collections.emptyMap());
            fallback.put("delta", Collections.emptyMap());
            fallback.put("meta", Collections.emptyMap());
            fallback.put("shadowAvailable", false);
            return fallback;
        }
    }

    private String deriveOnlineStatus(Device device) {
        Integer status = device.getStatus();
        if (status == null) {
            return "unknown";
        }
        if (status == 0) {
            return "unactivated";
        }
        if (status == 2) {
            return "offline";
        }
        if (status == 1) {
            return isHeartbeatStale(device) ? "stale" : "online";
        }
        return "unknown";
    }

    private boolean isHeartbeatStale(Device device) {
        LocalDateTime lastHeartbeatTime = device.getLastHeartbeatTime();
        if (lastHeartbeatTime == null) {
            return false;
        }
        return lastHeartbeatTime.isBefore(LocalDateTime.now().minusSeconds(heartbeatStaleSeconds));
    }
}
