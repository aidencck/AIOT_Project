package com.aiot.gateway.security;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一 401 返回格式（Result JSON）。
 */
public class ResultServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ResultServerAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        // 缺少登录态：保持旧行为提示
        String msg = "缺少或无效的 Authorization 头";
        return write(exchange.getResponse(), HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    public Mono<Void> writeUnauthorizedWithMessage(ServerHttpResponse response, String message) {
        String msg = (message == null || message.isBlank())
                ? ResultCode.UNAUTHORIZED.getMessage()
                : message;
        return write(response, HttpStatus.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getCode(), msg);
    }

    private Mono<Void> write(ServerHttpResponse response, HttpStatus httpStatus, Integer code, String message) {
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Result<Object> result = Result.fail(code, message);
        byte[] body = toJsonBytes(result);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private byte[] toJsonBytes(Result<Object> result) {
        try {
            return objectMapper.writeValueAsBytes(result);
        } catch (JsonProcessingException ex) {
            return ("{\"code\":401,\"message\":\"" + ResultCode.UNAUTHORIZED.getMessage() + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
    }
}
