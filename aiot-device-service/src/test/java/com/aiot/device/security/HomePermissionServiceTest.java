package com.aiot.device.security;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.common.http.CrossServiceHttpProperties;
import com.aiot.device.utils.UserContext;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HomePermissionServiceTest {

    private HttpServer server;
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedToken = new AtomicReference<>();
    private final AtomicReference<String> capturedUserId = new AtomicReference<>();
    private HomePermissionService homePermissionService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/homes/home-1/permission/check", exchange -> {
            capturedPath.set(exchange.getRequestURI().toString());
            capturedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            capturedUserId.set(exchange.getRequestHeaders().getFirst("X-User-Id"));
            byte[] body = "{\"code\":200,\"message\":\"操作成功\",\"data\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setTimeoutMs(1500L);
        properties.setMaxRetries(0);
        properties.setRetryBackoffMs(1L);
        properties.setCircuitBreakerFailureThreshold(3);
        properties.setCircuitBreakerOpenMs(1000L);
        properties.validate();

        homePermissionService = new HomePermissionService(new CrossServiceHttpExecutor(properties));
        ReflectionTestUtils.setField(homePermissionService, "homeServiceBaseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(homePermissionService, "internalToken", "inner-token-2026");

        UserContext.set(new UserContext.UserInfo("u-1", "13800000000"));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        UserContext.remove();
    }

    @Test
    void requireHomePermission_shouldCallInternalEndpointWithTrustedHeaders() {
        homePermissionService.requireHomePermission("home-1", 3, "deny");

        assertEquals("/api/v1/internal/homes/home-1/permission/check?minRole=3", capturedPath.get());
        assertEquals("inner-token-2026", capturedToken.get());
        assertEquals("u-1", capturedUserId.get());
    }

    @Test
    void requireHomePermission_shouldRejectWhenInternalTokenMissing() {
        ReflectionTestUtils.setField(homePermissionService, "internalToken", "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> homePermissionService.requireHomePermission("home-1", 3, "deny"));
        assertEquals(ResultCode.UNAUTHORIZED, ex.getResultCode());
    }
}
