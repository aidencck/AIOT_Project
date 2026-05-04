package com.aiot.shadow.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class DeviceEventPendingRecoveryScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceEventSubscriber subscriber;
    private final String streamKey;
    private final String group;
    private final String consumer;
    private final boolean enabled;
    private final int maxBatchSize;
    private final long minIdleMs;
    private final int maxDeliveryCount;
    private final Counter scannedCounter;
    private final Counter claimedCounter;
    private final Counter recoveredCounter;
    private final Counter failedCounter;

    public DeviceEventPendingRecoveryScheduler(
            StringRedisTemplate stringRedisTemplate,
            DeviceEventSubscriber subscriber,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.device-status-stream-group:aiot-shadow-service-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-shadow-service}") String consumer,
            @Value("${aiot.events.pending-reclaim.enabled:true}") boolean enabled,
            @Value("${aiot.events.pending-reclaim.max-batch-size:64}") int maxBatchSize,
            @Value("${aiot.events.pending-reclaim.min-idle-ms:60000}") long minIdleMs,
            @Value("${aiot.events.pending-reclaim.max-delivery-count:10}") int maxDeliveryCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.subscriber = subscriber;
        this.streamKey = streamKey;
        this.group = group;
        this.consumer = consumer;
        this.enabled = enabled;
        this.maxBatchSize = Math.max(maxBatchSize, 1);
        this.minIdleMs = Math.max(minIdleMs, 0L);
        this.maxDeliveryCount = Math.max(maxDeliveryCount, 1);
        this.scannedCounter = Counter.builder("aiot.stream.pending.scanned.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.claimedCounter = Counter.builder("aiot.stream.pending.claimed.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.recoveredCounter = Counter.builder("aiot.stream.pending.recovered.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aiot.stream.pending.recovery.failed.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${aiot.events.pending-reclaim.fixed-delay-ms:30000}")
    public void recoverPending() {
        if (!enabled) {
            return;
        }
        try {
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream().pending(
                    streamKey, group, Range.unbounded(), maxBatchSize
            );
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }
            List<RecordId> ids = new ArrayList<>();
            for (PendingMessage pendingMessage : pendingMessages) {
                scannedCounter.increment();
                if (pendingMessage.getTotalDeliveryCount() > maxDeliveryCount) {
                    continue;
                }
                ids.add(pendingMessage.getId());
            }
            if (ids.isEmpty()) {
                return;
            }
            List<MapRecord<String, Object, Object>> claimedRecords = stringRedisTemplate.opsForStream().claim(
                    streamKey, group, consumer, Duration.ofMillis(minIdleMs), ids.toArray(new RecordId[0])
            );
            if (claimedRecords == null || claimedRecords.isEmpty()) {
                return;
            }
            claimedCounter.increment(claimedRecords.size());
            log.info("Recovering shadow pending stream records, stream={}, group={}, consumer={}, size={}, minIdleMs={}",
                    streamKey, group, consumer, claimedRecords.size(), minIdleMs);
            for (MapRecord<String, Object, Object> record : claimedRecords) {
                try {
                    subscriber.onMessage(castRecord(record));
                    recoveredCounter.increment();
                } catch (Exception ex) {
                    failedCounter.increment();
                    log.warn("Failed to replay reclaimed shadow message, stream={}, group={}, consumer={}, recordId={}",
                            streamKey, group, consumer, record.getId(), ex);
                }
            }
        } catch (Exception ex) {
            failedCounter.increment();
            log.warn("Failed to recover shadow pending stream records, stream={}, group={}, consumer={}",
                    streamKey, group, consumer, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, String, String> castRecord(MapRecord<String, Object, Object> record) {
        return (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) record;
    }
}
