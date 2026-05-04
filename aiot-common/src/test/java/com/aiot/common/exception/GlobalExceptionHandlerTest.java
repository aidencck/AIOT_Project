package com.aiot.common.exception;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.config.SkipResponseWrap;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void shouldReturnParamMissingWhenRequestParamAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MissingServletRequestParameterException ex =
                new MissingServletRequestParameterException("deviceId", "String");

        Result<?> result = (Result<?>) handler.handleMissingServletRequestParameterException(ex, request, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals(ResultCode.PARAM_MISSING.getCode(), result.getCode());
    }

    @Test
    void shouldReturnMethodNotAllowedWhenHttpMethodUnsupported() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("POST", List.of("GET"));

        Result<?> result = (Result<?>) handler.handleHttpRequestMethodNotSupportedException(ex, request, response);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED.value(), response.getStatus());
        assertEquals(ResultCode.METHOD_NOT_ALLOWED.getCode(), result.getCode());
    }

    @Test
    void shouldMapResponseStatusExceptionToUnifiedResultCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "resource missing");

        Result<?> result = (Result<?>) handler.handleResponseStatusException(ex, request, response);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
        assertEquals(ResultCode.RESOURCE_NOT_FOUND.getCode(), result.getCode());
        assertEquals("resource missing", result.getMessage());
    }

    @Test
    void shouldMapBusinessExceptionToConflict() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BusinessException ex = new BusinessException(ResultCode.DEVICE_OFFLINE, "device offline");

        Result<?> result = (Result<?>) handler.handleBusinessException(ex, request, response);

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals(ResultCode.DEVICE_OFFLINE.getCode(), result.getCode());
    }

    @Test
    void shouldHandleMethodArgumentNotValidException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = ProtocolController.class.getDeclaredMethod("validateArg", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "req");
        bindingResult.addError(new FieldError("req", "name", "名称不能为空"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        Result<?> result = (Result<?>) handler.handleMethodArgumentNotValidException(ex, request, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals(ResultCode.VALIDATE_FAILED.getCode(), result.getCode());
        assertEquals("名称不能为空", result.getMessage());
    }

    @Test
    void shouldHandleMethodArgumentTypeMismatchException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = ProtocolController.class.getDeclaredMethod("validateArg", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Integer.class, "page", parameter, null);

        Result<?> result = (Result<?>) handler.handleMethodArgumentTypeMismatchException(ex, request, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals(ResultCode.VALIDATE_FAILED.getCode(), result.getCode());
    }

    @Test
    void shouldHandleHttpMessageNotReadableException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
                "invalid json",
                new MockHttpInputMessage("{".getBytes(StandardCharsets.UTF_8)));

        Result<?> result = (Result<?>) handler.handleHttpMessageNotReadableException(ex, request, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals(ResultCode.REQUEST_BODY_INVALID.getCode(), result.getCode());
    }

    @Test
    void shouldHandleNoHandlerFoundException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        NoHandlerFoundException ex =
                new NoHandlerFoundException("GET", "/api/v1/home/not-exists", new HttpHeaders());

        Result<?> result = (Result<?>) handler.handleNoHandlerFoundException(ex, request, response);

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
        assertEquals(ResultCode.RESOURCE_NOT_FOUND.getCode(), result.getCode());
    }

    @Test
    void shouldHandleConstraintViolationException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = (ConstraintViolation<Object>) mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("参数非法");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        Result<?> result = (Result<?>) handler.handleConstraintViolationException(ex, request, response);

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals(ResultCode.VALIDATE_FAILED.getCode(), result.getCode());
        assertEquals("参数非法", result.getMessage());
    }

    @Test
    void shouldMapResponseStatusException5xxToFailed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        Result<?> result = (Result<?>) handler.handleResponseStatusException(ex, request, response);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals(ResultCode.FAILED.getCode(), result.getCode());
    }

    @Test
    void shouldReturnDenyForEmqxProtocolPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/emqx/auth");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Object result = handler.handleException(new RuntimeException("boom"), request, response);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("text/plain", response.getContentType());
        assertInstanceOf(String.class, result);
        assertEquals("deny", result);
    }

    @Test
    void shouldReturnDenyWhenSkipResponseWrapControllerThrowsException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/protocol/custom");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Method method = ProtocolController.class.getDeclaredMethod("callback");
        request.setAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE, new HandlerMethod(new ProtocolController(), method));

        Object result = handler.handleException(new RuntimeException("boom"), request, response);

        assertEquals(HttpStatus.OK.value(), response.getStatus());
        assertEquals("text/plain", response.getContentType());
        assertEquals("deny", result);
    }

    @SkipResponseWrap
    static class ProtocolController {
        public void callback() {
        }

        public void validateArg(String value) {
        }
    }
}
