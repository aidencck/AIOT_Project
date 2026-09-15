package com.aiot.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
public class RedisStreamPendingRecoveryScheduler {

    private final StringRedisTemplate stringRedisTemplate;
    private final Consumer<MapRecord<String, String, String>> replayer;
    private final String streamKey;
    private final String group;
    private final String consumer;
    private final int maxBatchSize;
    private final long minIdleMs;
    private final int maxDeliveryCount;
    private final Counter scannedCounter;
    private final Counter claimedCounter;
    private final Counter recoveredCounter;
    private final Counter failedCounter;
    private final Counter poisonCounter;
    private final Counter discardedCounter;

    public RedisStreamPendingRecoveryScheduler(
            StringRedisTemplate stringRedisTemplate,
            Consumer<MapRecord<String, String, String>> replayer,
            MeterRegistry meterRegistry,
            String serviceTag,
            String streamKey,
            String group,
            String consumer,
            int maxBatchSize,
            long minIdleMs,
            int maxDeliveryCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.replayer = replayer;
        this.streamKey = streamKey;
        this.group = group;
        this.consumer = consumer;
        this.maxBatchSize = Math.max(maxBatchSize, 1);
        this.minIdleMs = Math.max(minIdleMs, 0L);
        this.maxDeliveryCount = Math.max(maxDeliveryCount, 1);
        this.scannedCounter = Counter.builder("aiot.stream.pending.scanned.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.claimedCounter = Counter.builder("aiot.stream.pending.claimed.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.recoveredCounter = Counter.builder("aiot.stream.pending.recovered.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aiot.stream.pending.recovery.failed.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.poisonCounter = Counter.builder("aiot.stream.pending.poison.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
        this.discardedCounter = Counter.builder("aiot.stream.pending.discarded.total")
                .tag("service", serviceTag)
                .tag("stream", streamKey)
                .register(meterRegistry);
    }

    public synchronized void recoverPending() {
        try {
            PendingMessages pendingMessages = stringRedisTemplate.opsForStream().pending(
                    streamKey, group, Range.unbounded(), maxBatchSize
            );
            if (pendingMessages == null || pendingMessages.isEmpty()) {
                return;
            }
            List<RecordId> ids = new ArrayList<>();
            Set<RecordId> poisonIds = new HashSet<>();
            for (PendingMessage pendingMessage : pendingMessages) {
                scannedCounter.increment();
                if (pendingMessage.getTotalDeliveryCount() > maxDeliveryCount) {
                    poisonCounter.increment();
                    poisonIds.add(pendingMessage.getId());
                    log.error("Poison stream record exceeded max delivery count, will force-ack on replay failure, "
                                    + "stream={}, group={}, recordId={}, deliveries={}, maxDeliveryCount={}",
                            streamKey, group, pendingMessage.getId(), pendingMessage.getTotalDeliveryCount(), maxDeliveryCount);
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
            log.info("Recovering pending stream records, stream={}, group={}, consumer={}, size={}, minIdleMs={}",
                    streamKey, group, consumer, claimedRecords.size(), minIdleMs);
            for (MapRecord<String, Object, Object> record : claimedRecords) {
                try {
                    replayer.accept(castRecord(record));
                    recoveredCounter.increment();
                } catch (Exception ex) {
                    failedCounter.increment();
                    if (poisonIds.contains(record.getId())) {
                        discardPoison(record, ex);
                    } else {
                        log.warn("Failed to replay reclaimed message, stream={}, group={}, consumer={}, recordId={}",
                                streamKey, group, consumer, record.getId(), ex);
                    }
                }
            }
        } catch (Exception ex) {
            failedCounter.increment();
            log.warn("Failed to recover pending stream records, stream={}, group={}, consumer={}",
                    streamKey, group, consumer, ex);
        }
    }

    private void discardPoison(MapRecord<String, Object, Object> record, Exception error) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(streamKey, group, record.getId());
            discardedCounter.increment();
            log.error("Discarded poison stream record to avoid permanent PEL accumulation, "
                            + "stream={}, group={}, consumer={}, recordId={}, error={}",
                    streamKey, group, consumer, record.getId(), error.getMessage());
        } catch (Exception ackEx) {
            log.error("Failed to ACK poison stream record, stream={}, group={}, consumer={}, recordId={}",
                    streamKey, group, consumer, record.getId(), ackEx);
        }
    }

    @SuppressWarnings("unchecked")
    private MapRecord<String, String, String> castRecord(MapRecord<String, Object, Object> record) {
        return (MapRecord<String, String, String>) (MapRecord<?, ?, ?>) record;
    }
}
