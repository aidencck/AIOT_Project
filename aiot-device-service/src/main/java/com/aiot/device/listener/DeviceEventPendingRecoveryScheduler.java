package com.aiot.device.listener;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class DeviceEventPendingRecoveryScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final DeviceStatusStreamSubscriber subscriber;
    private final String streamKey;
    private final String group;
    private final String consumer;
    private final boolean enabled;
    private final int maxBatchSize;
    private final Counter recoveredCounter;
    private final Counter failedCounter;

    public DeviceEventPendingRecoveryScheduler(
            StringRedisTemplate stringRedisTemplate,
            DeviceStatusStreamSubscriber subscriber,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String streamKey,
            @Value("${aiot.events.device-status-stream-group:aiot-device-service-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-device-service}") String consumer,
            @Value("${aiot.events.pending-reclaim.enabled:true}") boolean enabled,
            @Value("${aiot.events.pending-reclaim.max-batch-size:64}") int maxBatchSize) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.subscriber = subscriber;
        this.streamKey = streamKey;
        this.group = group;
        this.consumer = consumer;
        this.enabled = enabled;
        this.maxBatchSize = maxBatchSize;
        this.recoveredCounter = Counter.builder("aiot.stream.pending.recovered.total")
                .tag("service", "aiot-device-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aiot.stream.pending.recovery.failed.total")
                .tag("service", "aiot-device-service")
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${aiot.events.pending-reclaim.fixed-delay-ms:30000}")
    public void recoverPending() {
        if (!enabled) {
            return;
        }
        try {
            List<MapRecord<String, Object, Object>> pendingRecords = stringRedisTemplate.opsForStream().read(
                    Consumer.from(group, consumer),
                    StreamReadOptions.empty().count(maxBatchSize).block(Duration.ofMillis(100)),
                    StreamOffset.create(streamKey, ReadOffset.from("0-0"))
            );
            if (pendingRecords == null || pendingRecords.isEmpty()) {
                return;
            }
            log.info("Recovering pending stream records, stream={}, group={}, consumer={}, size={}",
                    streamKey, group, consumer, pendingRecords.size());
            for (MapRecord<String, Object, Object> record : pendingRecords) {
                subscriber.onMessage(castRecord(record));
                recoveredCounter.increment();
            }
        } catch (Exception ex) {
            failedCounter.increment();
            log.warn("Failed to recover pending stream records, stream={}, group={}, consumer={}",
                    streamKey, group, consumer, ex);
        }
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, String, String> castRecord(MapRecord<String, Object, Object> record) {
        return (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) record;
    }
}
