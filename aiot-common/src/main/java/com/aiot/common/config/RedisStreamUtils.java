package com.aiot.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Locale;
import java.util.Map;

@Slf4j
public class RedisStreamUtils {

    public static void ensureConsumerGroup(StringRedisTemplate stringRedisTemplate, String streamKey, String group) {
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
            if (!isBusyGroupException(ex)) {
                throw ex;
            }
            log.info("Stream consumer group already exists, stream={}, group={}", streamKey, group);
        }
    }

    public static boolean isBusyGroupException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
