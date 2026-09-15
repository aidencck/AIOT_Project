package com.aiot.device.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.http.CrossServiceHttpExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminConsoleRemoteQueryFacadeImplTest {

    @Test
    void shouldUseContentAliasesForGatePassedAndReportPath() {
        CrossServiceHttpExecutor executor = mock(CrossServiceHttpExecutor.class);
        when(executor.execute(anyString(), anySupplier())).thenReturn(Result.success(Map.of(
                "sceneType", "OFFLINE_FLAP",
                "reportType", "regression_gate",
                "exists", true,
                "generatedAt", 123456789L,
                "content", Map.of(
                        "gatePassed", true,
                        "reportPath", "/tmp/offline_flap_regression_gate.json",
                        "total", 3
                )
        )));
        AdminConsoleRemoteQueryFacadeImpl facade = new AdminConsoleRemoteQueryFacadeImpl(
                executor,
                WebClient.builder(),
                "http://127.0.0.1:8083",
                "http://127.0.0.1:8084"
        );

        var response = facade.loadAiEvalRegressionGate("OFFLINE_FLAP");

        assertEquals("/tmp/offline_flap_regression_gate.json", response.getReportPath());
        assertTrue(Boolean.TRUE.equals(response.getGatePassed()));
        assertEquals(3, response.getContent().get("total"));
    }

    @Test
    void shouldFallbackToTopLevelPayloadWhenContentMissing() {
        CrossServiceHttpExecutor executor = mock(CrossServiceHttpExecutor.class);
        when(executor.execute(anyString(), anySupplier())).thenReturn(Result.success(Map.of(
                "sceneType", "SHADOW_DIFF",
                "reportType", "regression_gate",
                "reportPath", "/tmp/shadow_diff_regression_gate.json",
                "exists", false,
                "generatedAt", 2233445566L,
                "total", 0,
                "schema_pass_rate", 0.0
        )));
        AdminConsoleRemoteQueryFacadeImpl facade = new AdminConsoleRemoteQueryFacadeImpl(
                executor,
                WebClient.builder(),
                "http://127.0.0.1:8083",
                "http://127.0.0.1:8084"
        );

        var response = facade.loadAiEvalRegressionGate("SHADOW_DIFF");

        assertEquals("/tmp/shadow_diff_regression_gate.json", response.getReportPath());
        assertFalse(Boolean.TRUE.equals(response.getExists()));
        assertEquals(0, response.getContent().get("total"));
        assertEquals(0.0, response.getContent().get("schema_pass_rate"));
    }

    @SuppressWarnings("unchecked")
    private Supplier<Mono<Result<Map<String, Object>>>> anySupplier() {
        return any(Supplier.class);
    }
}
