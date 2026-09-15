package com.aiot.common.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;

import java.time.Duration;
import java.util.Objects;

@Slf4j
public class RedisStreamListenerContainerFactory {

    public static StreamMessageListenerContainer<String, MapRecord<String, String, String>> create(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            StreamListener<String, MapRecord<String, String, String>> listener,
            String stream,
            String group,
            String consumer,
            long pollTimeoutMs,
            int batchSize,
            String logTag) {
        return create(connectionFactory, stringRedisTemplate, listener, stream, group, consumer, 1,
                pollTimeoutMs, batchSize, null, logTag);
    }

    public static StreamMessageListenerContainer<String, MapRecord<String, String, String>> create(
            RedisConnectionFactory connectionFactory,
            StringRedisTemplate stringRedisTemplate,
            StreamListener<String, MapRecord<String, String, String>> listener,
            String stream,
            String group,
            String consumer,
            int consumerCount,
            long pollTimeoutMs,
            int batchSize,
            MeterRegistry meterRegistry,
            String logTag) {
        RedisStreamUtils.ensureConsumerGroup(stringRedisTemplate, stream, group);
        Counter loopErrorCounter = meterRegistry == null ? null : Counter.builder("aiot.stream.consume.loop.error.total")
                .tag("service", logTag)
                .tag("stream", stream)
                .register(meterRegistry);
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofMillis(Math.max(pollTimeoutMs, 100L)))
                        .batchSize(Math.max(batchSize, 1))
                        .errorHandler(error -> {
                            if (loopErrorCounter != null) {
                                loopErrorCounter.increment();
                            }
                            log.error(logTag + " stream consume loop error, stream={}, group={}, consumer={}",
                                    stream, group, consumer, error);
                        })
                        .build();
        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(Objects.requireNonNull(connectionFactory), options);
        int count = Math.max(consumerCount, 1);
        for (int i = 0; i < count; i++) {
            String consumerName = count == 1 ? consumer : consumer + "-" + i;
            container.receive(
                    Consumer.from(group, consumerName),
                    StreamOffset.create(stream, ReadOffset.lastConsumed()),
                    listener);
        }
        log.info(logTag + " stream consumer started, stream={}, group={}, consumer={}, consumerCount={}",
                stream, group, consumer, count);
        return container;
    }
}
