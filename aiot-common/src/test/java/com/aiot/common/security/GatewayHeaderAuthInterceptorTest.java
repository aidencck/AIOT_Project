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

@SuppressWarnings("null")
class GatewayHeaderAuthInterceptorTest {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_PHONE_HEADER = "X-User-Phone";
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

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
        request.addHeader(USER_PHONE_HEADER, "13800000000");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u1001", RequestUserContext.get().getUserId());
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
    void shouldAllowInternalRequestWithValidTokenAndBindContextWhenHeaderPresent() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/internal/sync");
        request.addHeader(INTERNAL_TOKEN_HEADER, "secret-token");
        request.addHeader(USER_ID_HEADER, "internal-user");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("internal-user", RequestUserContext.get().getUserId());
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
        RequestUserContext.set(new RequestUserContext.UserInfo("u1", "138"));

        interceptor.afterCompletion(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object(), null);

        assertNull(RequestUserContext.get());
    }
}
