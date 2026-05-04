package com.aiot.common.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Resolve whether current request should bypass unified response wrapping.
 */
@Component
public class ResponseContractResolver {

    private static final Set<String> RAW_PROTOCOL_PATH_PREFIXES = Set.of("/api/v1/emqx");

    public boolean shouldBypassResponseWrap(MethodParameter returnType, String requestPath) {
        return hasSkipResponseWrap(returnType) || isRawProtocolPath(requestPath);
    }

    public boolean shouldUseProtocolFallback(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (handler instanceof HandlerMethod handlerMethod && hasSkipResponseWrap(handlerMethod)) {
            return true;
        }
        return isRawProtocolPath(resolveRequestPath(request));
    }

    private boolean hasSkipResponseWrap(MethodParameter returnType) {
        if (returnType == null) {
            return false;
        }
        return AnnotatedElementUtils.hasAnnotation(returnType.getContainingClass(), SkipResponseWrap.class)
                || returnType.hasMethodAnnotation(SkipResponseWrap.class);
    }

    private boolean hasSkipResponseWrap(HandlerMethod handlerMethod) {
        if (handlerMethod == null) {
            return false;
        }
        Method method = handlerMethod.getMethod();
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), SkipResponseWrap.class)
                || AnnotatedElementUtils.hasAnnotation(method, SkipResponseWrap.class);
    }

    private boolean isRawProtocolPath(String path) {
        if (path == null) {
            return false;
        }
        return RAW_PROTOCOL_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String resolveRequestPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!StringUtils.hasText(uri)) {
            uri = request.getServletPath();
        }
        return uri;
    }
}
