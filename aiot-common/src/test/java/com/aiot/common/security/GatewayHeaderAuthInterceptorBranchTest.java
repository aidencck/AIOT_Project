package com.aiot.common.security;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayHeaderAuthInterceptorBranchTest {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final GatewayHeaderAuthInterceptor interceptor = new GatewayHeaderAuthInterceptor();

    @AfterEach
    void tearDown() {
        RequestUserContext.remove();
    }

    @Test
    void shouldRejectInternalRequestWhenConfiguredTokenIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/internal/home/check");
        request.addHeader(INTERNAL_TOKEN_HEADER, "provided");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }

    @Test
    void shouldLeaveContextEmptyForInternalRequestWithoutUserHeader() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "internal-secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/internal/home/check");
        request.addHeader(INTERNAL_TOKEN_HEADER, "internal-secret");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertNull(RequestUserContext.get());
    }

    @Test
    void shouldFallbackGlobalUserIdToUserIdForNormalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/home/users");
        request.addHeader(USER_ID_HEADER, "u-normal");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u-normal", RequestUserContext.get().getUserId());
        assertEquals("u-normal", RequestUserContext.get().getGlobalUserId());
    }

    @Test
    void shouldRejectNonBearerAuthorizationEvenWhenJwtFallbackEnabled() {
        ReflectionTestUtils.setField(interceptor, "allowDirectUserJwt", true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/device/list");
        request.addHeader("Authorization", "Basic abc");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }
}
