package com.aiot.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("null")
class ResponseContractResolverTest {

    private final ResponseContractResolver resolver = new ResponseContractResolver();

    @Test
    void shouldBypassWrapWhenMethodHasSkipAnnotation() throws Exception {
        Method method = MethodAnnotatedController.class.getDeclaredMethod("callback");
        MethodParameter returnType = new MethodParameter(method, -1);

        boolean bypass = resolver.shouldBypassResponseWrap(returnType, "/api/v1/normal/path");

        assertTrue(bypass);
    }

    @Test
    void shouldBypassWrapWhenPathMatchesProtocolPrefix() throws Exception {
        Method method = PlainController.class.getDeclaredMethod("callback");
        MethodParameter returnType = new MethodParameter(method, -1);

        boolean bypass = resolver.shouldBypassResponseWrap(returnType, "/api/v1/emqx/auth");

        assertTrue(bypass);
    }

    @Test
    void shouldUseProtocolFallbackWhenBestMatchingHandlerIsAnnotated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/other-protocol/callback");
        Method method = TypeAnnotatedController.class.getDeclaredMethod("callback");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE,
                new HandlerMethod(new TypeAnnotatedController(), method));

        boolean protocolFallback = resolver.shouldUseProtocolFallback(request);

        assertTrue(protocolFallback);
    }

    @Test
    void shouldNotUseProtocolFallbackForNormalRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/home/users");

        boolean protocolFallback = resolver.shouldUseProtocolFallback(request);

        assertFalse(protocolFallback);
    }

    @Test
    void shouldUseProtocolFallbackWhenRequestUriIsBlankButServletPathMatches() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("");
        request.setServletPath("/api/v1/emqx/auth");

        boolean protocolFallback = resolver.shouldUseProtocolFallback(request);

        assertTrue(protocolFallback);
    }

    static class PlainController {
        public String callback() {
            return "ok";
        }
    }

    static class MethodAnnotatedController {
        @SkipResponseWrap
        public String callback() {
            return "ok";
        }
    }

    @SkipResponseWrap
    static class TypeAnnotatedController {
        public String callback() {
            return "ok";
        }
    }
}
