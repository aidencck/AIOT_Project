package com.aiot.rule.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.rule.service.RuleLifecycleService;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.MDC;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class DeviceEventSubscriber implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final RuleLifecycleService ruleLifecycleService;
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
                                 RuleLifecycleService ruleLifecycleService,
                                 StringRedisTemplate stringRedisTemplate,
                                 MeterRegistry meterRegistry,
                                 @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
                                 @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}") String dlqStream,
                                 @Value("${aiot.events.consume-retry.max-attempts:3}") int maxAttempts,
                                 @Value("${aiot.events.consume-retry.backoff-ms:200}") long retryBackoffMs,
                                 @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group) {
        this.objectMapper = objectMapper;
        this.ruleLifecycleService = ruleLifecycleService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = deviceStatusStream;
        this.dlqStream = dlqStream;
        this.group = group;
        this.maxAttempts = Math.max(maxAttempts, 1);
        this.retryBackoffMs = Math.max(retryBackoffMs, 0L);
        this.consumedCounter = Counter.builder("aiot.stream.consume.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.successCounter = Counter.builder("aiot.stream.consume.success.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.failedCounter = Counter.builder("aiot.stream.consume.failed.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.retriedCounter = Counter.builder("aiot.stream.consume.retried.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.dlqPublishedCounter = Counter.builder("aiot.stream.dlq.published.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", dlqStream)
                .register(meterRegistry);
        this.dlqPublishFailedCounter = Counter.builder("aiot.stream.dlq.publish.failed.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", dlqStream)
                .register(meterRegistry);
        this.ackSuccessCounter = Counter.builder("aiot.stream.ack.success.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.ackFailedCounter = Counter.builder("aiot.stream.ack.failed.total")
                .tag("service", "aiot-rule-engine")
                .tag("stream", deviceStatusStream)
                .register(meterRegistry);
        this.consumeLatencyTimer = Timer.builder("aiot.stream.consume.latency")
                .tag("service", "aiot-rule-engine")
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
            DeviceEvent event = objectMapper.readValue(body, DeviceEvent.class);
            if (event.getTraceId() != null) {
                MDC.put("traceId", event.getTraceId());
            }
            processWithRetry(event, message);
            shouldAck = true;
            successCounter.increment();
        } catch (Exception e) {
            failedCounter.increment();
            shouldAck = publishToDlq(message, body, e);
            log.warn("Failed to handle device event from stream, stream={}, recordId={}, rawBody={}",
                    message.getStream(), message.getId(), body, e);
            if (!shouldAck) {
                throw new RuntimeException("DLQ publish failed, keep message pending for retry", e);
            }
        } finally {
            if (shouldAck) {
                acknowledge(message);
            }
            consumeLatencyTimer.record(Duration.ofNanos(System.nanoTime() - startNanos));
            MDC.remove("traceId");
        }
    }

    private void processWithRetry(DeviceEvent event, MapRecord<String, String, String> message) throws Exception {
        int attempt = 1;
        while (true) {
            try {
                log.info("Received DeviceEvent from stream, eventType={}, deviceId={}, eventId={}, recordId={}, attempt={}",
                        event.getEventType(), event.getDeviceId(), event.getEventId(), message.getId(), attempt);
                ruleLifecycleService.executeByEvent(event);
                return;
            } catch (Exception ex) {
                if (attempt >= maxAttempts) {
                    throw ex;
                }
                attempt++;
                retriedCounter.increment();
                log.warn("Retry handling device event from stream, stream={}, recordId={}, nextAttempt={}",
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
            stringRedisTemplate.opsForStream().acknowledge(deviceStatusStream, group, message.getId());
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
            stringRedisTemplate.opsForStream().add(StreamRecords.mapBacked(dlqPayload).withStreamKey(dlqStream));
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
