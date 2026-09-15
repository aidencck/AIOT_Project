package com.aiot.device.service;

import com.aiot.device.repository.DeviceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DeviceStatusBufferService {

    public enum EnqueueResult {
        QUEUED,
        DROPPED_OVERFLOW
    }

    private final DeviceRepository deviceRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final String deviceStatusStream;
    private final String deviceStatusGroup;
    private final ConcurrentHashMap<String, BufferEntry> statusBuffer = new ConcurrentHashMap<>();
    private final Counter flushSuccessCounter;
    private final Counter flushFailureCounter;
    private final Counter flushDroppedCounter;
    private final Timer flushTimer;
    private final Counter bufferOverflowCounter;

    @Value("${aiot.device-status-buffer.max-batch-size:500}")
    private int maxBatchSize;

    @Value("${aiot.device-status-buffer.max-buffer-size:100000}")
    private int maxBufferSize;

    public DeviceStatusBufferService(
            DeviceRepository deviceRepository,
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${aiot.events.device-status-stream:aiot:stream:device-event}") String deviceStatusStream,
            @Value("${aiot.events.device-status-stream-group:aiot-device-service-group}") String deviceStatusGroup) {
        this.deviceRepository = deviceRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.deviceStatusStream = deviceStatusStream;
        this.deviceStatusGroup = deviceStatusGroup;
        this.flushSuccessCounter = Counter.builder("aiot.device.status.flush.success.total")
                .description("Total number of device status updates flushed to database")
                .register(meterRegistry);
        this.flushFailureCounter = Counter.builder("aiot.device.status.flush.failed.total")
                .description("Total number of device status updates failed to flush to database")
                .register(meterRegistry);
        this.flushDroppedCounter = Counter.builder("aiot.device.status.flush.dropped.total")
                .description("Total number of device statuses dropped because device record was not found")
                .register(meterRegistry);
        this.flushTimer = Timer.builder("aiot.device.status.flush.duration")
                .description("Device status buffer flush latency")
                .register(meterRegistry);
        this.bufferOverflowCounter = Counter.builder("aiot.device.status.buffer.overflow.total")
                .description("Total device statuses dropped due to buffer full")
                .register(meterRegistry);
        Gauge.builder("aiot.device.status.buffer.pending.size", statusBuffer, ConcurrentHashMap::size)
                .description("Current pending device status count in buffer")
                .register(meterRegistry);
    }

    public EnqueueResult enqueue(String globalDeviceId, Integer status, String recordId) {
        if (!StringUtils.hasText(globalDeviceId) || status == null) {
            return EnqueueResult.QUEUED;
        }
        if (!statusBuffer.containsKey(globalDeviceId) && statusBuffer.size() >= maxBufferSize) {
            bufferOverflowCounter.increment();
            log.warn("Device status buffer full, keep stream record pending for retry, deviceId={}, recordId={}",
                    globalDeviceId, recordId);
            return EnqueueResult.DROPPED_OVERFLOW;
        }
        statusBuffer.compute(globalDeviceId, (key, existing) -> {
            Set<String> recordIds = new LinkedHashSet<>();
            if (existing != null) {
                recordIds.addAll(existing.recordIds);
            }
            if (recordId != null) {
                recordIds.add(recordId);
            }
            return new BufferEntry(status, recordIds);
        });
        return EnqueueResult.QUEUED;
    }

    @Scheduled(fixedDelayString = "${aiot.device-status-buffer.flush-interval-ms:1000}")
    public void flush() {
        if (statusBuffer.isEmpty()) {
            return;
        }

        long startNanos = System.nanoTime();
        List<Map.Entry<String, BufferEntry>> claimed = new ArrayList<>(Math.min(maxBatchSize, statusBuffer.size()));
        for (Map.Entry<String, BufferEntry> entry : statusBuffer.entrySet()) {
            if (statusBuffer.remove(entry.getKey(), entry.getValue())) {
                claimed.add(entry);
                if (claimed.size() >= maxBatchSize) {
                    break;
                }
            }
        }
        if (claimed.isEmpty()) {
            return;
        }

        Map<Integer, List<Map.Entry<String, BufferEntry>>> groupedByStatus = new HashMap<>();
        for (Map.Entry<String, BufferEntry> entry : claimed) {
            groupedByStatus.computeIfAbsent(entry.getValue().status, key -> new ArrayList<>()).add(entry);
        }

        int processed = claimed.size();
        int success = 0;
        int failed = 0;
        int dropped = 0;
        List<String> ackRecordIds = new ArrayList<>();

        for (Map.Entry<Integer, List<Map.Entry<String, BufferEntry>>> group : groupedByStatus.entrySet()) {
            Integer status = group.getKey();
            List<Map.Entry<String, BufferEntry>> entries = group.getValue();
            List<String> globalDeviceIds = new ArrayList<>(entries.size());
            for (Map.Entry<String, BufferEntry> entry : entries) {
                globalDeviceIds.add(entry.getKey());
            }
            try {
                int affected = deviceRepository.updateStatusByGlobalDeviceIds(globalDeviceIds, status);
                dropped += Math.max(0, entries.size() - affected);
                success += entries.size();
                for (Map.Entry<String, BufferEntry> entry : entries) {
                    ackRecordIds.addAll(entry.getValue().recordIds);
                }
            } catch (Exception ex) {
                failed += entries.size();
                log.warn("Flush device status failed, status={}, batchSize={}", status, entries.size(), ex);
            }
        }

        acknowledgeRecords(ackRecordIds);

        if (success > 0) {
            flushSuccessCounter.increment(success);
        }
        if (failed > 0) {
            flushFailureCounter.increment(failed);
        }
        if (dropped > 0) {
            flushDroppedCounter.increment(dropped);
        }
        flushTimer.record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);

        if (success > 0 || failed > 0 || dropped > 0) {
            log.info("Flushed device status buffer, processed={}, success={}, failed={}, dropped={}, acked={}, pending={}",
                    processed, success, failed, dropped, ackRecordIds.size(), statusBuffer.size());
        }
    }

    private void acknowledgeRecords(List<String> recordIds) {
        if (recordIds.isEmpty()) {
            return;
        }
        try {
            Long acked = stringRedisTemplate.opsForStream().acknowledge(
                    deviceStatusStream,
                    deviceStatusGroup,
                    recordIds.toArray(new String[0]));
            log.info("Acknowledged device status stream records, stream={}, group={}, count={}",
                    deviceStatusStream, deviceStatusGroup, acked);
        } catch (Exception ex) {
            log.warn("Failed to acknowledge device status stream records, stream={}, group={}, recordIds={}",
                    deviceStatusStream, deviceStatusGroup, recordIds, ex);
        }
    }

    private static final class BufferEntry {
        private final Integer status;
        private final Set<String> recordIds;

        private BufferEntry(Integer status, Set<String> recordIds) {
            this.status = status;
            this.recordIds = recordIds;
        }
    }
}
