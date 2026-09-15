package com.aiot.common.config;

import com.aiot.common.api.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.ResolvableType;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.aiot")
public class GlobalResponseHandler implements ResponseBodyAdvice<Object> {

    private final ObjectMapper objectMapper;
    private final ResponseContractResolver responseContractResolver;

    public GlobalResponseHandler(ObjectMapper objectMapper, ResponseContractResolver responseContractResolver) {
        this.objectMapper = objectMapper;
        this.responseContractResolver = responseContractResolver;
    }

    @Override
    public boolean supports(@NonNull MethodParameter returnType,
                            @NonNull Class<? extends HttpMessageConverter<?>> converterType) {
        Class<?> parameterType = returnType.getParameterType();
        if (Result.class.isAssignableFrom(parameterType)) {
            return false;
        }
        if (Resource.class.isAssignableFrom(parameterType)
                || StreamingResponseBody.class.isAssignableFrom(parameterType)
                || byte[].class.equals(parameterType)) {
            return false;
        }
        ResolvableType resolvableType = ResolvableType.forMethodParameter(returnType);
        Class<?> nestedClass = resolvableType.resolve();
        return nestedClass == null || !Result.class.isAssignableFrom(nestedClass);
    }

    @Override
    public Object beforeBodyWrite(@Nullable Object body,
                                  @NonNull MethodParameter returnType,
                                  @NonNull MediaType selectedContentType,
                                  @NonNull Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  @NonNull ServerHttpRequest request,
                                  @NonNull ServerHttpResponse response) {
        String path = request.getURI().getPath();
        if (responseContractResolver.shouldBypassResponseWrap(returnType, path)) {
            return body;
        }
        if (body instanceof Result
                || body instanceof Resource
                || body instanceof StreamingResponseBody
                || body instanceof byte[]) {
            return body;
        }
        if (selectedContentType != null
                && (MediaType.TEXT_EVENT_STREAM.includes(selectedContentType)
                || MediaType.APPLICATION_OCTET_STREAM.includes(selectedContentType))) {
            return body;
        }
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(Result.success(body));
            } catch (Exception e) {
                throw new RuntimeException("Error wrapping String response", e);
            }
        }
        return Result.success(body);
    }
}
