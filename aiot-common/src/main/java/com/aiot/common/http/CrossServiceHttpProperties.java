package com.aiot.common.http;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 跨服务 HTTP 调用统一策略配置
 */
@Component
@ConfigurationProperties(prefix = "aiot.cross-service.http")
public class CrossServiceHttpProperties {

    /**
     * 调用超时（毫秒）
     */
    private long timeoutMs = 2000L;

    /**
     * 最大重试次数（不含首次）
     */
    private int maxRetries = 1;

    /**
     * 重试退避（毫秒）
     */
    private long retryBackoffMs = 200L;

    /**
     * 熔断触发阈值：连续失败次数
     */
    private int circuitBreakerFailureThreshold = 3;

    /**
     * 熔断打开时长（毫秒）
     */
    private long circuitBreakerOpenMs = 10000L;

    @PostConstruct
    public void validate() {
        if (timeoutMs <= 0L) {
            throw new IllegalStateException("aiot.cross-service.http.timeout-ms must be > 0");
        }
        if (maxRetries < 0) {
            throw new IllegalStateException("aiot.cross-service.http.max-retries must be >= 0");
        }
        if (retryBackoffMs < 0L) {
            throw new IllegalStateException("aiot.cross-service.http.retry-backoff-ms must be >= 0");
        }
        if (circuitBreakerFailureThreshold <= 0) {
            throw new IllegalStateException("aiot.cross-service.http.circuit-breaker-failure-threshold must be > 0");
        }
        if (circuitBreakerOpenMs <= 0L) {
            throw new IllegalStateException("aiot.cross-service.http.circuit-breaker-open-ms must be > 0");
        }
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    public void setRetryBackoffMs(long retryBackoffMs) {
        this.retryBackoffMs = retryBackoffMs;
    }

    public int getCircuitBreakerFailureThreshold() {
        return circuitBreakerFailureThreshold;
    }

    public void setCircuitBreakerFailureThreshold(int circuitBreakerFailureThreshold) {
        this.circuitBreakerFailureThreshold = circuitBreakerFailureThreshold;
    }

    public long getCircuitBreakerOpenMs() {
        return circuitBreakerOpenMs;
    }

    public void setCircuitBreakerOpenMs(long circuitBreakerOpenMs) {
        this.circuitBreakerOpenMs = circuitBreakerOpenMs;
    }
}
