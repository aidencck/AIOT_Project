package com.aiot.shadow.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
public class DeviceEventSubscriber implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final String deviceStatusStream;
    private final String dlqStream;
    private final String group;
    private final int maxAttempts;
    private final long retryBackoffMs;
    private final Counter consumedCounter;
    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter retriedCounter;
    private final Counter dlqPublishedCounter;
    private final Counter dlqPublishFailedCounter;
    private final Counter ackSuccessCounter;
    private final Counter ackFailedCounter;
    private final Timer consumeLatencyTimer;

    public DeviceEventSubscriber(ObjectMapper objectMapper,
                                 StringRedisTemplate stringRedisTemplate,
                                 MeterRegistry meterRegistry,
                                 @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
                                 @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}") String dlqStream,
                                 @Value("${aiot.events.consume-retry.max-attempts:3}") int maxAttempts,
                                 @Value("${aiot.events.consume-retry.backoff-ms:200}") long retryBackoffMs,
                                 @Value("${aiot.events.device-status-stream-group:aiot-shadow-service-group}") String group) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = Objects.requireNonNull(deviceStatusStream, "deviceStatusStream");
        this.dlqStream = Objects.requireNonNull(dlqStream, "dlqStream");
        this.group = Objects.requireNonNull(group, "group");
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.retryBackoffMs = Math.max(retryBackoffMs, 0L);
        this.consumedCounter = Counter.builder("aiot.stream.consume.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.successCounter = Counter.builder("aiot.stream.consume.success.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aiot.stream.consume.failed.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.retriedCounter = Counter.builder("aiot.stream.consume.retried.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.dlqPublishedCounter = Counter.builder("aiot.stream.dlq.published.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", dlqStream)
                .register(meterRegistry);
        this.dlqPublishFailedCounter = Counter.builder("aiot.stream.dlq.publish.failed.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", dlqStream)
                .register(meterRegistry);
        this.ackSuccessCounter = Counter.builder("aiot.stream.ack.success.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.ackFailedCounter = Counter.builder("aiot.stream.ack.failed.total")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.consumeLatencyTimer = Timer.builder("aiot.stream.consume.latency")
                .tag("service", "aiot-shadow-service")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        long startNanos = System.nanoTime();
        consumedCounter.increment();
        String body = message.getValue().get("payload");
        boolean shouldAck = false;
        try {
            if (body == null) {
                log.warn("Skip stream message without payload, stream={}, recordId={}", message.getStream(), message.getId());
                shouldAck = true;
                successCounter.increment();
                return;
            }
            DeviceEvent event = processWithRetry(body, message);
            if (event.getEventType() == DeviceEventType.SHADOW_DESIRED_UPDATED
                    || event.getEventType() == DeviceEventType.SHADOW_REPORTED_UPDATED) {
                log.info("Shadow event consumed, eventType={}, deviceId={}, eventId={}, version={}, recordId={}",
                        event.getEventType(), event.getDeviceId(), event.getEventId(), event.getVersion(), message.getId());
            }
            shouldAck = true;
            successCounter.increment();
        } catch (Exception ex) {
            failedCounter.increment();
            shouldAck = publishToDlq(message, body, ex);
            log.warn("Failed to consume device event from stream, stream={}, recordId={}, rawBody={}",
                    message.getStream(), message.getId(), body, ex);
            if (!shouldAck) {
                throw new RuntimeException("DLQ publish failed, keep message pending for retry", ex);
            }
        } finally {
            if (shouldAck) {
                acknowledge(message);
            }
            consumeLatencyTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
        }
    }

    private DeviceEvent processWithRetry(String body, MapRecord<String, String, String> message) throws Exception {
        int attempt = 1;
        while (true) {
            try {
                return objectMapper.readValue(body, DeviceEvent.class);
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                attempt++;
                retriedCounter.increment();
                log.warn("Retry consume shadow event, stream={}, recordId={}, nextAttempt={}",
                        message.getStream(), message.getId(), attempt, ex);
                sleepQuietly(retryBackoffMs);
            }
        }
    }

    private void sleepQuietly(long sleepMs) {
        if (sleepMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean acknowledge(MapRecord<String, String, String> message) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    Objects.requireNonNull(deviceStatusStream, "deviceStatusStream"),
                    Objects.requireNonNull(group, "group"),
                    Objects.requireNonNull(message.getId(), "recordId"));
            ackSuccessCounter.increment();
            return true;
        } catch (Exception ex) {
            ackFailedCounter.increment();
            log.warn("Failed to ACK stream message, stream={}, group={}, recordId={}",
                    deviceStatusStream, group, message.getId(), ex);
            return false;
        }
    }

    private boolean publishToDlq(MapRecord<String, String, String> message, String body, Exception error) {
        try {
            Map<String, String> dlqPayload = new LinkedHashMap<>();
            dlqPayload.put("sourceStream", deviceStatusStream);
            dlqPayload.put("recordId", String.valueOf(message.getId()));
            dlqPayload.put("consumerGroup", group);
            dlqPayload.put("failedAt", Instant.now().toString());
            dlqPayload.put("error", error.getClass().getSimpleName() + ":" + error.getMessage());
            dlqPayload.put("maxAttempts", String.valueOf(maxAttempts));
            dlqPayload.put("payload", body == null ? "" : body);
            stringRedisTemplate.opsForStream().add(
                    StreamRecords.mapBacked(dlqPayload)
                            .withStreamKey(Objects.requireNonNull(dlqStream, "dlqStream")));
            dlqPublishedCounter.increment();
            return true;
        } catch (Exception ex) {
            dlqPublishFailedCounter.increment();
            log.warn("Failed to publish stream message to DLQ, dlqStream={}, recordId={}",
                    dlqStream, message.getId(), ex);
            return false;
        }
    }
}
