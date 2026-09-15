package com.aiot.common.http;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossServiceHttpPropertiesTest {

    @Test
    void shouldAcceptValidProperties() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();

        properties.setTimeoutMs(1000L);
        properties.setMaxRetries(2);
        properties.setRetryBackoffMs(100L);
        properties.setCircuitBreakerFailureThreshold(3);
        properties.setCircuitBreakerOpenMs(2000L);

        assertDoesNotThrow(properties::validate);
    }

    @Test
    void shouldRejectNonPositiveTimeout() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setTimeoutMs(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("aiot.cross-service.http.timeout-ms must be > 0", ex.getMessage());
    }

    @Test
    void shouldRejectNegativeRetryCount() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setMaxRetries(-1);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("aiot.cross-service.http.max-retries must be >= 0", ex.getMessage());
    }

    @Test
    void shouldRejectNegativeRetryBackoff() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setRetryBackoffMs(-1L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("aiot.cross-service.http.retry-backoff-ms must be >= 0", ex.getMessage());
    }

    @Test
    void shouldRejectNonPositiveFailureThreshold() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setCircuitBreakerFailureThreshold(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("aiot.cross-service.http.circuit-breaker-failure-threshold must be > 0", ex.getMessage());
    }

    @Test
    void shouldRejectNonPositiveCircuitOpenWindow() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setCircuitBreakerOpenMs(0L);

        IllegalStateException ex = assertThrows(IllegalStateException.class, properties::validate);
        assertEquals("aiot.cross-service.http.circuit-breaker-open-ms must be > 0", ex.getMessage());
    }
}
