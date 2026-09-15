package com.aiot.device.config;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.utils.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthInterceptorTest {

    private final AuthInterceptor interceptor = new AuthInterceptor();

    @AfterEach
    void clean() {
        UserContext.remove();
    }

    @Test
    void shouldBindGatewayHeadersToUserContext() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");
        request.addHeader("X-User-Id", "u-1");
        request.addHeader("X-Global-User-Id", "gu-1");
        request.addHeader("X-User-Phone", "13800000000");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u-1", UserContext.get().getUserId());
        assertEquals("gu-1", UserContext.get().getGlobalUserId());
        assertEquals("13800000000", UserContext.get().getPhone());
    }

    @Test
    void shouldRejectWhenGatewayHeadersMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/devices");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));

        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
        assertEquals("缺少用户身份，请通过网关访问", ex.getMessage());
    }

    @Test
    void shouldAuthorizeInternalCallByToken() {
        ReflectionTestUtils.setField(interceptor, "internalToken", "token-1");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/internal/homes/permission");
        request.addHeader("X-Internal-Token", "token-1");
        request.addHeader("X-User-Id", "u-2");
        request.addHeader("X-Global-User-Id", "gu-2");

        boolean allowed = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertTrue(allowed);
        assertEquals("u-2", UserContext.get().getUserId());
        assertEquals("gu-2", UserContext.get().getGlobalUserId());
    }
}
