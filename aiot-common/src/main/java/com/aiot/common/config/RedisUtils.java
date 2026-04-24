package com.aiot.common.config;

import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 规范： aiot:{module}:{entity}:{id}
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 构建符合规范的 Key
     */
    public String buildKey(String module, String entity, String id) {
        return String.format("aiot:%s:%s:%s", module, entity, id);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Object getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    public RecordId addToStream(String streamKey, Map<String, String> body) {
        MapRecord<String, String, String> record = StreamRecords.string(body).withStreamKey(streamKey);
        return redisTemplate.opsForStream().add(record);
    }
}
