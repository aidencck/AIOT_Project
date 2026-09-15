package com.aiot.device.config;

import com.aiot.common.config.RedisStreamListenerContainerFactory;
import com.aiot.device.listener.DeviceStatusStreamSubscriber;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Configuration
public class RedisDeviceEventStreamConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> deviceEventStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            DeviceStatusStreamSubscriber subscriber,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
            @Value("${aiot.events.device-status-stream-group:aiot-device-service-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-device-service}") String consumer) {
        return RedisStreamListenerContainerFactory.create(
                connectionFactory,
                stringRedisTemplate,
                subscriber,
                deviceStatusStream,
                group,
                consumer,
                2000L,
                64,
                "DeviceService");
    }
}
