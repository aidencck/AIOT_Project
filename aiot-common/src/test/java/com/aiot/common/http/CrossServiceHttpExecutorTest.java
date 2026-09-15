package com.aiot.common.http;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossServiceHttpExecutorTest {

    @Test
    void shouldRetryAndSucceedWithinConfiguredAttempts() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setTimeoutMs(1000L);
        properties.setMaxRetries(1);
        properties.setRetryBackoffMs(1L);
        properties.setCircuitBreakerFailureThreshold(3);
        properties.setCircuitBreakerOpenMs(1000L);
        properties.validate();
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(properties);

        AtomicInteger attempts = new AtomicInteger(0);
        String value = executor.execute("retry-case", () -> Mono.defer(() -> {
            int current = attempts.incrementAndGet();
            if (current == 1) {
                return Mono.error(new RuntimeException("first-failed"));
            }
            return Mono.just("ok");
        }));

        assertEquals("ok", value);
        assertEquals(2, attempts.get());
    }

    @Test
    void shouldOpenCircuitAfterConsecutiveFailures() {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setTimeoutMs(1000L);
        properties.setMaxRetries(0);
        properties.setRetryBackoffMs(1L);
        properties.setCircuitBreakerFailureThreshold(2);
        properties.setCircuitBreakerOpenMs(10000L);
        properties.validate();
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(properties);

        AtomicInteger attempts = new AtomicInteger(0);
        assertThrows(RuntimeException.class, () -> executor.execute("cb-case", () -> Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new RuntimeException("failed-1"));
        })));
        assertThrows(RuntimeException.class, () -> executor.execute("cb-case", () -> Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.error(new RuntimeException("failed-2"));
        })));

        BusinessException open = assertThrows(BusinessException.class,
                () -> executor.execute("cb-case", () -> Mono.just("never")));
        assertEquals(ResultCode.FAILED, open.getResultCode());
        assertEquals(2, attempts.get());
    }
}
