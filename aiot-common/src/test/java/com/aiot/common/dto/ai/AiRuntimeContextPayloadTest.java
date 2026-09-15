package com.aiot.common.dto.ai;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiRuntimeContextPayloadTest {

    @Test
    void shouldConvertLegacyDeviceContextToRuntimePayload() {
        AiDeviceContextResp legacy = AiDeviceContextResp.builder()
                .sceneType("OFFLINE_FLAP")
                .deviceId("gdev-1")
                .globalDeviceId("gdev-1")
                .authIdentity("auth-1")
                .deviceSn("sn-1")
                .deviceName("device-name")
                .productKey("pk-1")
                .homeId("home-1")
                .roomId("room-1")
                .gatewayId("gateway-1")
                .status(1)
                .firmwareVersion("1.0.0")
                .lastHeartbeatTime("2026-08-07T01:00:00")
                .onlineStatus("OFFLINE")
                .thingModelJson("{\"properties\":[]}")
                .deviceModelJson("{\"schema\":\"aiot.device-model/v1\",\"properties\":[]}")
                .shadowSummary(Map.of("reported", Map.of()))
                .recentEvents(List.of(Map.of("eventId", "evt-1")))
                .opsSummary(Map.of("opsHint", "legacy"))
                .build();

        AiRuntimeContextPayload payload = AiRuntimeContextPayload.fromLegacy(legacy);

        assertNotNull(payload);
        assertNotNull(payload.getRuntimeContext());
        assertEquals("gdev-1", payload.getRuntimeContext().getDeviceId());
        assertEquals("gdev-1", payload.getRuntimeContext().getGlobalDeviceId());
        assertEquals("auth-1", payload.getRuntimeContext().getAuthIdentity());
        assertEquals("sn-1", payload.getRuntimeContext().getDeviceSn());
        assertEquals("room-1", payload.getRuntimeContext().getRoomId());
        assertEquals("gateway-1", payload.getRuntimeContext().getGatewayId());
        assertEquals("OFFLINE", payload.getRuntimeContext().getOnlineStatus());
        assertEquals("{\"properties\":[]}", payload.getThingModelJson());
        assertEquals("{\"schema\":\"aiot.device-model/v1\",\"properties\":[]}", payload.getDeviceModelJson());
    }
}
