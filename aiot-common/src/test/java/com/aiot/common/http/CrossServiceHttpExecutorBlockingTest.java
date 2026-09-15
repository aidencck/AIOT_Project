package com.aiot.common.http;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrossServiceHttpExecutorBlockingTest {

    @Test
    void shouldTimeoutWhenBlockingCallExceedsConfiguredDeadline() {
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(buildProperties(30L, 1, 20L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.executeBlocking("blocking-timeout", () -> {
                    try {
                        Thread.sleep(120L);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    return "late";
                }));

        assertEquals(ResultCode.FAILED, ex.getResultCode());
        assertEquals("跨服务调用超时", ex.getMessage());
    }

    @Test
    void shouldRethrowBusinessExceptionFromBlockingSupplier() {
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(buildProperties(100L, 2, 20L));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.executeBlocking("blocking-business-error",
                        () -> {
                            throw new BusinessException(ResultCode.FORBIDDEN, "denied");
                        }));

        assertEquals(ResultCode.FORBIDDEN, ex.getResultCode());
        assertEquals("denied", ex.getMessage());
    }

    @Test
    void shouldNotRetryBusinessExceptionForReactiveCall() {
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(buildProperties(100L, 3, 20L));
        AtomicInteger attempts = new AtomicInteger(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> executor.execute("reactive-business-error", () -> Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return Mono.error(new BusinessException(ResultCode.FORBIDDEN, "forbidden"));
                })));

        assertEquals(ResultCode.FORBIDDEN, ex.getResultCode());
        assertEquals(1, attempts.get());
    }

    @Test
    void shouldAllowRequestAgainAfterCircuitBreakerWindowExpires() throws InterruptedException {
        CrossServiceHttpExecutor executor = new CrossServiceHttpExecutor(buildProperties(100L, 1, 40L));

        assertThrows(RuntimeException.class,
                () -> executor.execute("recoverable-circuit", () -> Mono.error(new RuntimeException("boom"))));
        assertThrows(BusinessException.class,
                () -> executor.execute("recoverable-circuit", () -> Mono.just("blocked")));

        Thread.sleep(60L);

        String result = executor.execute("recoverable-circuit", () -> Mono.just("ok"));
        assertEquals("ok", result);
    }

    private CrossServiceHttpProperties buildProperties(long timeoutMs, int maxRetries, long circuitOpenMs) {
        CrossServiceHttpProperties properties = new CrossServiceHttpProperties();
        properties.setTimeoutMs(timeoutMs);
        properties.setMaxRetries(maxRetries);
        properties.setRetryBackoffMs(1L);
        properties.setCircuitBreakerFailureThreshold(1);
        properties.setCircuitBreakerOpenMs(circuitOpenMs);
        properties.validate();
        return properties;
    }
}
