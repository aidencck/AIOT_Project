package com.aiot.common.http;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 统一跨服务调用执行器：超时、重试、熔断
 */
@Component
public class CrossServiceHttpExecutor {

    private final CrossServiceHttpProperties properties;
    private final Map<String, SimpleCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public CrossServiceHttpExecutor(CrossServiceHttpProperties properties) {
        this.properties = properties;
    }

    public <T> T execute(String circuitKey, Supplier<Mono<T>> requestSupplier) {
        SimpleCircuitBreaker breaker = circuitBreakers.computeIfAbsent(
                circuitKey,
                key -> new SimpleCircuitBreaker(
                        properties.getCircuitBreakerFailureThreshold(),
                        properties.getCircuitBreakerOpenMs()
                )
        );
        breaker.preCheck();
        try {
            T result = requestSupplier.get()
                    .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                    .retryWhen(Retry.backoff(
                                    properties.getMaxRetries(),
                                    Duration.ofMillis(properties.getRetryBackoffMs())
                            )
                            .filter(this::shouldRetry)
                            .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                    .block();
            breaker.onSuccess();
            return result;
        } catch (RuntimeException ex) {
            breaker.onFailure();
            throw ex;
        }
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof BusinessException) {
            return false;
        }
        return true;
    }

    static class SimpleCircuitBreaker {

        private final int failureThreshold;
        private final long openMs;
        private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        private volatile long openUntilMs = 0L;

        SimpleCircuitBreaker(int failureThreshold, long openMs) {
            this.failureThreshold = failureThreshold;
            this.openMs = openMs;
        }

        synchronized void preCheck() {
            long now = System.currentTimeMillis();
            if (now < openUntilMs) {
                throw new BusinessException(ResultCode.FAILED, "跨服务调用已熔断，请稍后重试");
            }
            if (openUntilMs > 0L && now >= openUntilMs) {
                openUntilMs = 0L;
            }
        }

        synchronized void onSuccess() {
            consecutiveFailures.set(0);
            openUntilMs = 0L;
        }

        synchronized void onFailure() {
            int failed = consecutiveFailures.incrementAndGet();
            if (failed >= failureThreshold) {
                openUntilMs = System.currentTimeMillis() + openMs;
                consecutiveFailures.set(0);
            }
        }
    }
}
