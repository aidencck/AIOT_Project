package com.aiot.gateway.security;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.security.jwt.AiotJwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.impl.DefaultClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtReactiveAuthenticationManagerTest {

    @Mock
    private AiotJwtService jwtService;

    @Test
    void shouldAuthenticateAndExposePrincipal() {
        JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(jwtService);
        String token = "mock-token";
        Claims claims = new DefaultClaims();
        claims.setSubject("1001");
        claims.put("phone", "13800000000");
        when(jwtService.verify(token)).thenReturn(claims);

        Authentication auth = manager.authenticate(new JwtPreAuthToken(token)).block();
        assertNotNull(auth);
        assertEquals(true, auth.isAuthenticated());
        assertEquals(AiotJwtPrincipal.class, auth.getPrincipal().getClass());

        AiotJwtPrincipal principal = (AiotJwtPrincipal) auth.getPrincipal();
        assertEquals("1001", principal.userId());
        assertEquals("13800000000", principal.phone());
        verify(jwtService).verify(token);
    }

    @Test
    void shouldTranslateBusinessExceptionToBadCredentials() {
        JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(jwtService);
        String token = "expired-token";
        when(jwtService.verify(token)).thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "Token 已过期"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> manager.authenticate(new JwtPreAuthToken(token)).block());
        assertEquals(true, ex.getCause() instanceof BadCredentialsException);
        assertEquals("Token 已过期", ex.getCause().getMessage());
    }

    @Test
    void shouldRejectWhenAuthorizationHeaderMissing() {
        JwtReactiveAuthenticationManager manager = new JwtReactiveAuthenticationManager(jwtService);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> manager.authenticate(new JwtPreAuthToken(null)).block());
        assertEquals(true, ex.getCause() instanceof BadCredentialsException);
        assertEquals("缺少或无效的 Authorization 头", ex.getCause().getMessage());
    }
}
