package com.aiot.rule.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.rule.service.RuleLifecycleService;
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

    public DeviceEventSubscriber(ObjectMapper objectMapper,
                                 RuleLifecycleService ruleLifecycleService,
                                 StringRedisTemplate stringRedisTemplate,
                                 @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
                                 @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}") String dlqStream,
                                 @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group) {
        this.objectMapper = objectMapper;
        this.ruleLifecycleService = ruleLifecycleService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = deviceStatusStream;
        this.dlqStream = dlqStream;
        this.group = group;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String body = message.getValue().get("payload");
        try {
            if (body == null) {
                log.warn("Skip stream message without payload, stream={}, recordId={}", message.getStream(), message.getId());
                acknowledge(message);
                return;
            }
            DeviceEvent event = objectMapper.readValue(body, DeviceEvent.class);
            if (event.getTraceId() != null) {
                MDC.put("traceId", event.getTraceId());
            }
            log.info("Received DeviceEvent from stream, eventType={}, deviceId={}, eventId={}, recordId={}",
                    event.getEventType(), event.getDeviceId(), event.getEventId(), message.getId());
            ruleLifecycleService.executeByEvent(event);
            acknowledge(message);
        } catch (Exception e) {
            publishToDlq(message, body, e);
            acknowledge(message);
            log.warn("Failed to handle device event from stream, stream={}, recordId={}, rawBody={}",
                    message.getStream(), message.getId(), body, e);
        } finally {
            MDC.remove("traceId");
        }
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(deviceStatusStream, group, message.getId());
        } catch (Exception ex) {
            log.warn("Failed to ACK stream message, stream={}, group={}, recordId={}",
                    deviceStatusStream, group, message.getId(), ex);
        }
    }

    private void publishToDlq(MapRecord<String, String, String> message, String body, Exception error) {
        try {
            Map<String, String> dlqPayload = new LinkedHashMap<>();
            dlqPayload.put("sourceStream", deviceStatusStream);
            dlqPayload.put("recordId", String.valueOf(message.getId()));
            dlqPayload.put("consumerGroup", group);
            dlqPayload.put("failedAt", Instant.now().toString());
            dlqPayload.put("error", error.getClass().getSimpleName() + ":" + error.getMessage());
            dlqPayload.put("payload", body == null ? "" : body);
            stringRedisTemplate.opsForStream().add(StreamRecords.mapBacked(dlqPayload).withStreamKey(dlqStream));
        } catch (Exception ex) {
            log.warn("Failed to publish stream message to DLQ, dlqStream={}, recordId={}",
                    dlqStream, message.getId(), ex);
        }
    }
}
