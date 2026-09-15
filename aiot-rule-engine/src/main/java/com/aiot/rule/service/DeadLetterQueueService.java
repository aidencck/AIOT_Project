package com.aiot.rule.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DLQ（死信队列）运维服务。
 * 消费失败的消息由 DeviceEventSubscriber 写入 device-status-dlq-stream，
 * 这里提供死信列表查询、单条重放与数量统计，供运维入口使用。
 */
@Slf4j
@Service
public class DeadLetterQueueService {

    private final StringRedisTemplate stringRedisTemplate;
    private final String dlqStream;
    private final String deviceStatusStream;

    public DeadLetterQueueService(
            StringRedisTemplate stringRedisTemplate,
            @Value("${aiot.events.device-status-dlq-stream:aiot:stream:device-event:dlq}") String dlqStream,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.dlqStream = dlqStream;
        this.deviceStatusStream = deviceStatusStream;
    }

    /**
     * 读取最近 N 条死信（最新在前）。使用 XREVRANGE 反向区间读取，反序列化为 map。
     */
    public List<Map<String, Object>> listDeadLetters(long count) {
        long limit = Math.max(count, 1L);
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .reverseRange(dlqStream, Range.unbounded(), Limit.limit().count((int) limit));
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(records.size());
        for (MapRecord<String, Object, Object> record : records) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("recordId", record.getId() == null ? null : record.getId().getValue());
            Map<Object, Object> value = record.getValue();
            if (value != null) {
                value.forEach((k, v) -> entry.put(String.valueOf(k), v));
            }
            result.add(entry);
        }
        return result;
    }

    /**
     * 重放指定死信：从 DLQ 读取该条记录，取出 payload 字段重新 XADD 回主 stream。
     */
    public String replay(String recordId) {
        List<MapRecord<String, Object, Object>> records = stringRedisTemplate.opsForStream()
                .range(dlqStream, Range.closed(recordId, recordId));
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("DLQ record not found: " + recordId);
        }
        Map<Object, Object> value = records.get(0).getValue();
        Object payload = value == null ? null : value.get("payload");
        if (payload == null) {
            throw new IllegalArgumentException("DLQ record has no payload: " + recordId);
        }
        RecordId replayedId = stringRedisTemplate.opsForStream().add(
                StreamRecords.mapBacked(Map.of("payload", String.valueOf(payload)))
                        .withStreamKey(deviceStatusStream));
        log.info("Replayed DLQ record, dlqStream={}, recordId={}, replayedRecordId={}, targetStream={}",
                dlqStream, recordId, replayedId, deviceStatusStream);
        return replayedId == null ? null : replayedId.getValue();
    }

    /**
     * DLQ 当前堆积数量（XLEN）。
     */
    public long deadLetterCount() {
        Long size = stringRedisTemplate.opsForStream().size(dlqStream);
        return size == null ? 0L : size;
    }

    /**
     * 清空全部死信条目（XTRIM MAXLEN 0），返回清理前的堆积数量。
     */
    public long purge() {
        long count = deadLetterCount();
        Long trimmed = stringRedisTemplate.opsForStream().trim(dlqStream, 0);
        if (trimmed == null) {
            log.warn("XTRIM MAXLEN 0 returned null, dlqStream={}", dlqStream);
        }
        return count;
    }
}
