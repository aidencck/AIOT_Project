package com.aiot.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayUserHeaderGlobalFilterTest {

    @Test
    void shouldStripSpoofedUserHeadersWithoutAuthentication() {
        GatewayUserHeaderGlobalFilter filter = new GatewayUserHeaderGlobalFilter();
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/homes")
                        .header("X-User-Id", "spoofed-user")
                        .header("X-Global-User-Id", "spoofed-global-user")
                        .build()
        );

        AtomicReference<String> userId = new AtomicReference<>();
        AtomicReference<String> globalUserId = new AtomicReference<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            userId.set(ex.getRequest().getHeaders().getFirst("X-User-Id"));
            globalUserId.set(ex.getRequest().getHeaders().getFirst("X-Global-User-Id"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainCalled.get());
        assertEquals(null, userId.get());
        assertEquals(null, globalUserId.get());
    }

    @Test
    void shouldInjectInternalTokenForInternalPath() {
        GatewayUserHeaderGlobalFilter filter = new GatewayUserHeaderGlobalFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "internal-token-1234567890");

        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/internal/sync")
                        .header("X-Internal-Token", "spoofed-token")
                        .build()
        );

        AtomicReference<String> internalToken = new AtomicReference<>();
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainCalled.set(true);
            internalToken.set(ex.getRequest().getHeaders().getFirst("X-Internal-Token"));
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();
        assertTrue(chainCalled.get());
        assertEquals("internal-token-1234567890", internalToken.get());
    }
}
