package com.aiot.rule.config;

import com.aiot.common.config.RedisStreamListenerContainerFactory;
import com.aiot.rule.listener.DeviceEventSubscriber;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

@Configuration
public class RedisEventListenerConfig {

    @Bean(initMethod = "start", destroyMethod = "stop")
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>> deviceEventStreamContainer(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            DeviceEventSubscriber deviceEventSubscriber,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
            @Value("${aiot.events.device-status-stream-group:aiot-rule-engine-group}") String group,
            @Value("${aiot.events.device-status-stream-consumer:aiot-rule-engine}") String consumer,
            @Value("${aiot.events.consume.consumer-count:2}") int consumerCount,
            @Value("${aiot.events.consume.poll-timeout-ms:2000}") long pollTimeoutMs,
            @Value("${aiot.events.consume.batch-size:16}") int batchSize) {
        return RedisStreamListenerContainerFactory.create(
                connectionFactory,
                stringRedisTemplate,
                deviceEventSubscriber,
                deviceStatusStream,
                group,
                consumer,
                consumerCount,
                pollTimeoutMs,
                batchSize,
                meterRegistry,
                "RuleEngine");
    }
}
