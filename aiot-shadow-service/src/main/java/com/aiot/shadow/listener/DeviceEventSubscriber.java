package com.aiot.shadow.listener;

import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeviceEventSubscriber implements StreamListener<String, MapRecord<String, String, String>> {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final String deviceStatusStream;
    private final String group;

    public DeviceEventSubscriber(ObjectMapper objectMapper,
                                 StringRedisTemplate stringRedisTemplate,
                                 @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
                                 @Value("${aiot.events.device-status-stream-group:aiot-shadow-service-group}") String group) {
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = deviceStatusStream;
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
            if (event.getEventType() == DeviceEventType.SHADOW_DESIRED_UPDATED
                    || event.getEventType() == DeviceEventType.SHADOW_REPORTED_UPDATED) {
                log.info("Shadow event consumed, eventType={}, deviceId={}, eventId={}, version={}, recordId={}",
                        event.getEventType(), event.getDeviceId(), event.getEventId(), event.getVersion(), message.getId());
            }
            acknowledge(message);
        } catch (Exception ex) {
            log.warn("Failed to consume device event from stream, stream={}, recordId={}, rawBody={}",
                    message.getStream(), message.getId(), body, ex);
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
}
