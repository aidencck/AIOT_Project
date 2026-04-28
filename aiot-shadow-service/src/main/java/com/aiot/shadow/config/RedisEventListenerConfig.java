package com.aiot.shadow.config;

import com.aiot.shadow.listener.DeviceEventSubscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Configuration
public class RedisEventListenerConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> deviceEventStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            DeviceEventSubscriber deviceEventSubscriber,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
            @Value("${aiot.events.device-status-stream-group:aiot-shadow-service-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-shadow-service}") String consumer) {
        ensureConsumerGroup(stringRedisTemplate, deviceStatusStream, group);
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .batchSize(16)
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(Objects.requireNonNull(connectionFactory), options);
        Subscription subscription = container.receive(
                Consumer.from(group, consumer),
                StreamOffset.create(deviceStatusStream, ReadOffset.lastConsumed()),
                (StreamListener<String, MapRecord<String, String, String>>) Objects.requireNonNull(deviceEventSubscriber)
        );
        log.info("ShadowService stream consumer started, stream={}, group={}, consumer={}, active={}",
                deviceStatusStream, group, consumer, subscription.isActive());
        return container;
    }

    private void ensureConsumerGroup(StringRedisTemplate stringRedisTemplate, String streamKey, String group) {
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(streamKey))) {
            MapRecord<String, String, String> initRecord = StreamRecords.string(Map.of("bootstrap", "1"))
                    .withStreamKey(streamKey);
            RecordId recordId = stringRedisTemplate.opsForStream().add(initRecord);
            log.info("Initialized stream for consumer group creation, stream={}, recordId={}", streamKey, recordId);
        }
        try {
            stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.latest(), group);
            log.info("Created stream consumer group, stream={}, group={}", streamKey, group);
        } catch (Exception ex) {
            if (ex.getMessage() == null || !ex.getMessage().contains("BUSYGROUP")) {
                throw ex;
            }
            log.info("Stream consumer group already exists, stream={}, group={}", streamKey, group);
        }
    }
}
