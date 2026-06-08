package com.aiot.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityResultHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void authenticationEntryPointShouldReturnUnified401() throws Exception {
        ResultServerAuthenticationEntryPoint entryPoint = new ResultServerAuthenticationEntryPoint(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/homes").build());

        entryPoint.commence(exchange, new BadCredentialsException("whatever")).block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("code").asInt());
        assertEquals("缺少或无效的 Authorization 头", json.get("message").asText());
        assertTrue(json.get("data").isNull());
    }

    @Test
    void authenticationFailureHelperShouldReturnUnified401WithMessage() throws Exception {
        ResultServerAuthenticationEntryPoint entryPoint = new ResultServerAuthenticationEntryPoint(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/homes").build());

        entryPoint.writeUnauthorizedWithMessage(exchange.getResponse(), "无效或过期的 Token").block();

        assertEquals(401, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("code").asInt());
        assertEquals("无效或过期的 Token", json.get("message").asText());
        assertTrue(json.get("data").isNull());
    }

    @Test
    void accessDeniedHandlerShouldReturnUnified403() throws Exception {
        ResultServerAccessDeniedHandler handler = new ResultServerAccessDeniedHandler(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/homes").build());

        handler.handle(exchange, new AccessDeniedException("denied")).block();

        assertEquals(403, exchange.getResponse().getStatusCode().value());
        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(403, json.get("code").asInt());
        assertEquals("没有相关权限", json.get("message").asText());
        assertTrue(json.get("data").isNull());
    }
}

