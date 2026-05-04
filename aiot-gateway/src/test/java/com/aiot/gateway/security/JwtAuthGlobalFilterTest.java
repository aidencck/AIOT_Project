package com.aiot.gateway.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthGlobalFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReturnUnifiedJsonWhenAuthorizationMissing() throws Exception {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/homes").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainCalled.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("code").asInt());
        assertEquals("缺少或无效的 Authorization 头", json.get("message").asText());
        assertTrue(json.get("data").isNull());
    }

    @Test
    void shouldBypassWhitelistedPath() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(objectMapper);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v3/api-docs").build()
        );
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void shouldRemoveSpoofedHeadersAndInjectTrustedHeaders() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(objectMapper);
        String secret = "0123456789abcdef0123456789abcdef";
        ReflectionTestUtils.setField(filter, "jwtSecret", secret);

        String token = Jwts.builder()
                .setSubject("1001")
                .setId("jti-1001")
                .claim("phone", "13800000000")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/homes")
                        .header("Authorization", "Bearer " + token)
                        .header("X-User-Id", "spoofed-user")
                        .header("X-User-Phone", "spoofed-phone")
                        .build()
        );

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> phone = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            userId.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            phone.set(ex.getRequest().getHeaders().getFirst("X-User-Phone"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertEquals("1001", userId.get());
        assertEquals("13800000000", phone.get());
    }

    @Test
    void shouldRejectTokenWithoutJtiWhenRequired() throws Exception {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(objectMapper);
        String secret = "0123456789abcdef0123456789abcdef";
        ReflectionTestUtils.setField(filter, "jwtSecret", secret);
        ReflectionTestUtils.setField(filter, "requireJti", true);

        String token = Jwts.builder()
                .setSubject("1002")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/homes")
                        .header("Authorization", "Bearer " + token)
                        .build()
        );

        filter.filter(exchange, ex -> Mono.empty()).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        JsonNode json = objectMapper.readTree(body);
        assertEquals(401, json.get("code").asInt());
    }

    @Test
    void shouldInjectInternalTokenForInternalPath() {
        JwtAuthGlobalFilter filter = new JwtAuthGlobalFilter(objectMapper);
        String secret = "0123456789abcdef0123456789abcdef";
        ReflectionTestUtils.setField(filter, "jwtSecret", secret);
        ReflectionTestUtils.setField(filter, "internalToken", "internal-token-1234567890");

        String token = Jwts.builder()
                .setSubject("2001")
                .setId("jti-2001")
                .setExpiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/internal/sync")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Internal-Token", "spoofed-token")
                        .build()
        );

        AtomicReference<String> internalToken = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            internalToken.set(ex.getRequest().getHeaders().getFirst("X-Internal-Token"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertEquals("internal-token-1234567890", internalToken.get());
    }
}
