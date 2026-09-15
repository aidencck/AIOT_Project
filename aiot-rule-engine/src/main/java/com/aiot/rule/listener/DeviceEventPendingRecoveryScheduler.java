package com.aiot.rule.listener;

import com.aiot.common.config.RedisStreamPendingRecoveryScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceEventPendingRecoveryScheduler {

    private final boolean enabled;
    private final RedisStreamPendingRecoveryScheduler delegate;

    public DeviceEventPendingRecoveryScheduler(
            StringRedisTemplate stringRedisTemplate,
            DeviceEventSubscriber subscriber,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-rule-engine}") String consumer,
            @Value("${aiot.events.pending-reclaim.enabled:true}") boolean enabled,
            @Value("${aiot.events.pending-reclaim.max-batch-size:64}") int maxBatchSize,
            @Value("${aiot.events.pending-reclaim.min-idle-ms:60000}") long minIdleMs,
            @Value("${aiot.events.pending-reclaim.max-delivery-count:10}") int maxDeliveryCount) {
        this.enabled = enabled;
        this.delegate = new RedisStreamPendingRecoveryScheduler(
                stringRedisTemplate, subscriber::onMessage, meterRegistry, "aiot-rule-engine",
                streamKey, group, consumer, maxBatchSize, minIdleMs, maxDeliveryCount);
    }

    @Scheduled(fixedDelayString = "${aiot.events.pending-reclaim.fixed-delay-ms:30000}")
    public void recoverPending() {
        if (!enabled) {
            return;
        }
        delegate.recoverPending();
    }
}
