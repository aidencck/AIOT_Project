package com.aiot.auth.service.impl;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;
import com.aiot.auth.entity.DeviceCredential;
import com.aiot.auth.repository.DeviceCredentialRepository;
import com.aiot.auth.service.AuthMetrics;
import com.aiot.common.config.RedisUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static com.aiot.auth.utils.SignUtils.signWithHmacSha256;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private DeviceCredentialRepository credentialRepository;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private AuthMetrics authMetrics;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void authenticateDevice_shouldSupportAuthIdentityLookup() {
        EmqxAuthReq req = new EmqxAuthReq();
        req.setClientid("client-1");
        req.setUsername("auth-identity-1");
        req.setPassword(signWithHmacSha256("client-1", "secret-1"));

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-1");
        credential.setDeviceSecret("secret-1");
        when(credentialRepository.selectByIdentity("auth-identity-1")).thenReturn(credential);

        assertTrue(authService.authenticateDevice(req));
        verify(credentialRepository).selectByIdentity("auth-identity-1");
    }

    @Test
    void authenticateDevice_shouldRejectWhenIdentityNotFound() {
        EmqxAuthReq req = new EmqxAuthReq();
        req.setClientid("client-2");
        req.setUsername("missing-device");
        req.setPassword("bad-signature");
        when(credentialRepository.selectByIdentity("missing-device")).thenReturn(null);

        assertFalse(authService.authenticateDevice(req));
        verify(credentialRepository).selectByIdentity("missing-device");
    }

    @Test
    void handleDeviceStatusWebhook_shouldPublishWithGlobalDeviceId() {
        ReflectionTestUtils.setField(authService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authService, "deviceStatusStream", "aiot:stream:device-event");

        EmqxWebhookReq req = new EmqxWebhookReq();
        req.setAction("client.connected");
        req.setUsername("sn-001");
        req.setTimestamp(1_786_000_000L);

        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId("dev-1");
        credential.setGlobalDeviceId("gdev-1");
        credential.setAuthIdentity("auth-1");
        credential.setDeviceSn("sn-001");
        when(credentialRepository.selectByIdentity("sn-001")).thenReturn(credential);
        when(redisUtils.buildKey("device", "status", "gdev-1")).thenReturn("aiot:device:status:gdev-1");
        when(redisUtils.buildKey("auth", "credential-index", "sn-001")).thenReturn("aiot:auth:credential-index:sn-001");
        when(redisUtils.buildKey("auth", "credential", "gdev-1")).thenReturn("aiot:auth:credential:gdev-1");

        authService.handleDeviceStatusWebhook(req);

        verify(redisUtils).set("aiot:device:status:gdev-1", "online", 120L, java.util.concurrent.TimeUnit.SECONDS);
        verify(redisUtils).addToStream(eq("aiot:stream:device-event"), org.mockito.ArgumentMatchers.argThat(fields ->
                "gdev-1".equals(fields.get("deviceId"))
                        && "DEVICE_ONLINE".equals(fields.get("eventType"))
                        && fields.get("payload") != null
                        && fields.get("payload").contains("gdev-1")));
    }

    @Test
    void verifyWebhook_shouldPassViaInternalTokenWithoutSignature() {
        ReflectionTestUtils.setField(authService, "internalToken", "inner-token-2026");

        assertTrue(authService.verifyWebhook("client.connected", "client-1", "sn-001", 1_786_000_000L, null, "inner-token-2026"));
    }

    @Test
    void verifyWebhook_shouldRejectWhenInternalTokenMismatchAndSignatureMissing() {
        ReflectionTestUtils.setField(authService, "internalToken", "inner-token-2026");
        ReflectionTestUtils.setField(authService, "webhookSecret", "secret-1");

        assertFalse(authService.verifyWebhook("client.connected", "client-1", "sn-001", 1_786_000_000L, null, "wrong-token"));
    }
}
