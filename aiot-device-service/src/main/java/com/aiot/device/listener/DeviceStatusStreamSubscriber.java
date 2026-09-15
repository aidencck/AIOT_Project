package com.aiot.device.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.device.service.DeviceEventHistoryService;
import com.aiot.device.service.DeviceStatusBufferService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class DeviceStatusStreamSubscriber implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final DeviceStatusBufferService bufferService;
    private final DeviceEventHistoryService deviceEventHistoryService;
    private final StringRedisTemplate stringRedisTemplate;
    private final String deviceStatusStream;
    private final String dlqStream;
    private final String group;

    public DeviceStatusStreamSubscriber(
            ObjectMapper objectMapper,
            DeviceStatusBufferService bufferService,
            DeviceEventHistoryService deviceEventHistoryService,
            StringRedisTemplate stringRedisTemplate,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
            @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}") String dlqStream,
            @Value("${aiot.events.device-status-stream-group:aiot-device-service-group}") String group) {
        this.objectMapper = objectMapper;
        this.bufferService = bufferService;
        this.deviceEventHistoryService = deviceEventHistoryService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = deviceStatusStream;
        this.dlqStream = dlqStream;
        this.group = group;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        String body = message.getValue().get("payload");
        boolean shouldAck = false;
        try {
            if (body == null) {
                shouldAck = true;
                return;
            }
            DeviceEvent event = objectMapper.readValue(body, DeviceEvent.class);
            Integer status = mapStatus(event.getEventType());
            if (status == null || !StringUtils.hasText(event.getDeviceId())) {
                shouldAck = true;
                return;
            }
            // 先记录 per-device 事件历史，避免 buffer 失败导致历史丢失；record 内部已吞掉异常。
            deviceEventHistoryService.record(
                    event.getDeviceId(),
                    event.getEventType().name(),
                    status,
                    System.currentTimeMillis());
            DeviceStatusBufferService.EnqueueResult result =
                    bufferService.enqueue(event.getDeviceId(), status, message.getId().getValue());
            if (result == DeviceStatusBufferService.EnqueueResult.DROPPED_OVERFLOW) {
                log.warn("Device status buffer overflow, keep stream record pending for retry, stream={}, recordId={}, deviceId={}",
                        message.getStream(), message.getId(), event.getDeviceId());
            }
            // Buffered path: ACK is deferred until DeviceStatusBufferService.flush() persists to MySQL.
        } catch (Exception ex) {
            shouldAck = publishToDlq(message, body, ex);
            log.warn("Failed to consume device status stream event, stream={}, recordId={}",
                    message.getStream(), message.getId(), ex);
            if (!shouldAck) {
                throw new RuntimeException("DLQ publish failed, keep message pending for retry", ex);
            }
        } finally {
            if (shouldAck) {
                acknowledge(message);
            }
        }
    }

    private Integer mapStatus(DeviceEventType eventType) {
        if (eventType == DeviceEventType.DEVICE_ONLINE) {
            return 1;
        }
        if (eventType == DeviceEventType.DEVICE_OFFLINE) {
            return 2;
        }
        return null;
    }

    private void acknowledge(MapRecord<String, String, String> message) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(deviceStatusStream, group, message.getId());
        } catch (Exception ex) {
            log.warn("Failed to ACK stream message, stream={}, group={}, recordId={}",
                    deviceStatusStream, group, message.getId(), ex);
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
            dlqPayload.put("payload", body == null ? "" : body);
            stringRedisTemplate.opsForStream().add(StreamRecords.mapBacked(dlqPayload).withStreamKey(dlqStream));
            return true;
        } catch (Exception ex) {
            log.warn("Failed to publish stream message to DLQ, dlqStream={}, recordId={}",
                    dlqStream, message.getId(), ex);
            return false;
        }
    }
}
