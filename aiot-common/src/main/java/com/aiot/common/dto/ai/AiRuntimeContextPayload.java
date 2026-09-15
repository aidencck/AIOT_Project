package com.aiot.common.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRuntimeContextPayload {
    private AiRuntimeContext runtimeContext;
    private String thingModelJson;
    private String deviceModelJson;

    public static AiRuntimeContextPayload fromLegacy(AiDeviceContextResp legacyContext) {
        if (legacyContext == null) {
            return null;
        }
        return AiRuntimeContextPayload.builder()
                .runtimeContext(AiRuntimeContext.builder()
                        .sceneType(legacyContext.getSceneType())
                        .deviceId(legacyContext.getDeviceId())
                        .globalDeviceId(legacyContext.getGlobalDeviceId())
                        .authIdentity(legacyContext.getAuthIdentity())
                        .deviceSn(legacyContext.getDeviceSn())
                        .deviceName(legacyContext.getDeviceName())
                        .productKey(legacyContext.getProductKey())
                        .homeId(legacyContext.getHomeId())
                        .roomId(legacyContext.getRoomId())
                        .gatewayId(legacyContext.getGatewayId())
                        .status(legacyContext.getStatus())
                        .firmwareVersion(legacyContext.getFirmwareVersion())
                        .lastHeartbeatTime(legacyContext.getLastHeartbeatTime())
                        .onlineStatus(legacyContext.getOnlineStatus())
                        .shadowSummary(legacyContext.getShadowSummary())
                        .recentEvents(legacyContext.getRecentEvents())
                        .build())
                .thingModelJson(legacyContext.getThingModelJson())
                .deviceModelJson(legacyContext.getDeviceModelJson() == null
                        ? legacyContext.getThingModelJson()
                        : legacyContext.getDeviceModelJson())
                .build();
    }
}
