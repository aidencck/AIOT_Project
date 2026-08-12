package com.aiot.auth.service.impl;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.entity.DeviceCredential;
import com.aiot.auth.repository.DeviceCredentialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.aiot.auth.utils.SignUtils.signWithHmacSha256;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private DeviceCredentialRepository credentialRepository;

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
}
