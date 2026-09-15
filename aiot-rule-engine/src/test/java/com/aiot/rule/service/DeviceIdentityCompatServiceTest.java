package com.aiot.rule.service;

import com.aiot.common.dto.ai.AiRuntimeContext;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.rule.client.AiContextProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceIdentityCompatServiceTest {

    private AiContextProvider aiContextProvider;
    private DeviceIdentityCompatService service;

    @BeforeEach
    void setUp() {
        aiContextProvider = mock(AiContextProvider.class);
        service = new DeviceIdentityCompatService(aiContextProvider);
    }

    @Test
    void shouldResolveIdentityFromRuntimeContext() {
        when(aiContextProvider.getRuntimeContext("legacy-device-1", "OFFLINE_FLAP"))
                .thenReturn(AiRuntimeContextPayload.builder()
                        .runtimeContext(AiRuntimeContext.builder()
                                .deviceId("g-device-1")
                                .globalDeviceId("g-device-1")
                                .authIdentity("auth-device-1")
                                .deviceSn("sn-device-1")
                                .build())
                        .build());

        DeviceIdentityCompatService.DeviceIdentitySnapshot snapshot =
                service.resolveSnapshot("legacy-device-1", null, null, null);

        assertEquals("legacy-device-1", snapshot.deviceId());
        assertEquals("g-device-1", snapshot.globalDeviceId());
        assertEquals("auth-device-1", snapshot.authIdentity());
        assertEquals("sn-device-1", snapshot.deviceSn());
    }

    @Test
    void shouldFallbackToLegacyDeviceIdWhenRuntimeContextMissing() {
        when(aiContextProvider.getRuntimeContext("legacy-device-2", "OFFLINE_FLAP"))
                .thenReturn(null);

        DeviceIdentityCompatService.DeviceIdentitySnapshot snapshot =
                service.resolveSnapshot("legacy-device-2", null, null, null);

        assertEquals("legacy-device-2", snapshot.deviceId());
        assertEquals("legacy-device-2", snapshot.globalDeviceId());
        assertNull(snapshot.authIdentity());
        assertNull(snapshot.deviceSn());
    }

    @Test
    void shouldMatchKeywordAcrossNewAndLegacyIdentifiers() {
        assertTrue(service.matches("g-device-1", "legacy-device-1", "g-device-1", "auth-device-1", "sn-device-1"));
        assertTrue(service.matches("auth-device-1", "legacy-device-1", "g-device-1", "auth-device-1", "sn-device-1"));
        assertTrue(service.matches("sn-device-1", "legacy-device-1", "g-device-1", "auth-device-1", "sn-device-1"));
        assertTrue(service.matches("legacy-device-1", "legacy-device-1", "g-device-1", "auth-device-1", "sn-device-1"));
        assertFalse(service.matches("missing", "legacy-device-1", "g-device-1", "auth-device-1", "sn-device-1"));
    }
}
