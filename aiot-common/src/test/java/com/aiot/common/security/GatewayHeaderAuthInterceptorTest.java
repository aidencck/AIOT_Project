package com.aiot.common.security;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.security.jwt.AiotJwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class GatewayHeaderAuthInterceptorTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String GLOBAL_USER_ID_HEADER = "X-Global-User-Id";
    private static final String USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final GatewayHeaderAuthInterceptor interceptor = new GatewayHeaderAuthInterceptor();

    @AfterEach
    void tearDown() {
        RequestUserContext.remove();
    }

    @Test
    void shouldBindUserContextForNormalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/home/users");
        request.addHeader(USER_ID_HEADER, "u1001");
        request.addHeader(GLOBAL_USER_ID_HEADER, "gu1001");
        request.addHeader(USER_PHONE_HEADER, "13800000000");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u1001", RequestUserContext.get().getUserId());
        assertEquals("gu1001", RequestUserContext.get().getGlobalUserId());
        assertEquals("13800000000", RequestUserContext.get().getPhone());
    }

    @Test
    void shouldRejectWhenNormalRequestMissesUserId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/home/users");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }

    @Test
    void shouldBindUserContextFromJwtWhenDirectUserJwtFallbackEnabled() {
        AiotJwtService jwtService = mock(AiotJwtService.class);
        Claims claims = mock(Claims.class);
        when(jwtService.verify("valid-token")).thenReturn(claims);
        when(claims.getSubject()).thenReturn("u-from-jwt");
        when(claims.get("global_user_id", String.class)).thenReturn("gu-from-jwt");
        when(claims.get("phone", String.class)).thenReturn("13800001234");
        ReflectionTestUtils.setField(interceptor, "allowDirectUserJwt", true);
        ReflectionTestUtils.setField(interceptor, "jwtService", jwtService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/admin-console/ai/persistence/query");
        request.addHeader(AUTHORIZATION_HEADER, "Bearer valid-token");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u-from-jwt", RequestUserContext.get().getUserId());
        assertEquals("gu-from-jwt", RequestUserContext.get().getGlobalUserId());
        assertEquals("13800001234", RequestUserContext.get().getPhone());
    }

    @Test
    void shouldAllowInternalRequestWithValidTokenAndBindContextWhenHeaderPresent() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/internal/sync");
        request.addHeader(INTERNAL_TOKEN_HEADER, "secret-token");
        request.addHeader(USER_ID_HEADER, "internal-user");
        request.addHeader(GLOBAL_USER_ID_HEADER, "global-internal-user");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("internal-user", RequestUserContext.get().getUserId());
        assertEquals("global-internal-user", RequestUserContext.get().getGlobalUserId());
    }

    @Test
    void shouldRejectInternalRequestWhenTokenInvalid() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/internal/sync");
        request.addHeader(INTERNAL_TOKEN_HEADER, "bad-token");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }

    @Test
    void shouldClearContextAfterCompletion() {
        RequestUserContext.set(new RequestUserContext.UserInfo("u1", "gu1", "138"));

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertNull(RequestUserContext.get());
    }
}
