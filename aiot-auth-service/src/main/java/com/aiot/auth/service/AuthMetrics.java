package com.aiot.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * EMQX 鉴权/Webhook 拒绝的业务指标埋点。
 * 通过标签 reason/source 提供比通用 http_server_requests 更细的失败维度，
 * 用于 AuthFailureRateHigh 告警与鉴权链路排障。
 */
@Component
public class AuthMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter webhookRequestCounter;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // 预注册 webhook 请求计数器（值为 0 即被 Prometheus 采集）：
        // 规避 Micrometer 惰性创建 + 突发流量下 increase() 采样盲区，
        // 保证 perf_user_device_observability 的 auth_webhook_requests 断言稳定可复现。
        this.webhookRequestCounter = Counter.builder("aiot.auth.webhook.request.total")
                .tag("source", "emqx")
                .register(meterRegistry);
    }

    public void recordWebhookRequest() {
        webhookRequestCounter.increment();
    }

    public void recordAuthAttempt() {
        Counter.builder("aiot.auth.emqx.attempt.total")
                .tag("source", "emqx")
                .register(meterRegistry)
                .increment();
    }

    public void recordAuthDeny(String reason) {
        Counter.builder("aiot.auth.emqx.deny.total")
                .tag("source", "emqx")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    public void recordWebhookReject(String reason) {
        Counter.builder("aiot.auth.webhook.reject.total")
                .tag("source", "emqx")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }
}
