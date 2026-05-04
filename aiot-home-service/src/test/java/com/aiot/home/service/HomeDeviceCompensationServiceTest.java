package com.aiot.home.service;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.common.http.CrossServiceHttpProperties;
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

class HomeDeviceCompensationServiceTest {

    private HttpServer server;
    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();
    private final AtomicReference<String> capturedToken = new AtomicReference<>();
    private HomeDeviceCompensationService compensationService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/internal/devices/unbind/home/home-1", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().toString());
            capturedToken.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
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

        compensationService = new HomeDeviceCompensationService(new CrossServiceHttpExecutor(properties));
        ReflectionTestUtils.setField(compensationService, "deviceServiceBaseUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());
        ReflectionTestUtils.setField(compensationService, "internalToken", "inner-token-2026");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void unbindDevicesByHomeId_shouldCallInternalCompensationEndpoint() {
        compensationService.unbindDevicesByHomeId("home-1");

        assertEquals("POST", capturedMethod.get());
        assertEquals("/api/v1/internal/devices/unbind/home/home-1", capturedPath.get());
        assertEquals("inner-token-2026", capturedToken.get());
    }

    @Test
    void unbindDevicesByHomeId_shouldFailFastWhenTokenMissing() {
        ReflectionTestUtils.setField(compensationService, "internalToken", "");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> compensationService.unbindDevicesByHomeId("home-1"));
        assertEquals(ResultCode.FAILED, ex.getResultCode());
    }
}
