package com.aiot.device.service;

import com.aiot.device.repository.DeviceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class DeviceStatusBufferService {

    private final DeviceRepository deviceRepository;
    private final ConcurrentHashMap<String, Integer> statusBuffer = new ConcurrentHashMap<>();
    private final Counter flushSuccessCounter;
    private final Counter flushFailureCounter;
    private final Counter flushDroppedCounter;
    private final Timer flushTimer;

    @Value("${aiot.device-status-buffer.max-batch-size:500}")
    private int maxBatchSize;

    public DeviceStatusBufferService(DeviceRepository deviceRepository, MeterRegistry meterRegistry) {
        this.deviceRepository = deviceRepository;
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
    }

    public void enqueue(String deviceId, Integer status) {
        if (!StringUtils.hasText(deviceId) || status == null) {
            return;
        }
        statusBuffer.put(deviceId, status);
    }

    @Scheduled(fixedDelayString = "${aiot.device-status-buffer.flush-interval-ms:1000}")
    public void flush() {
        if (statusBuffer.isEmpty()) {
            return;
        }

        long startNanos = System.nanoTime();
        List<Map.Entry<String, Integer>> batch = new ArrayList<>(Math.min(maxBatchSize, statusBuffer.size()));
        for (Map.Entry<String, Integer> entry : statusBuffer.entrySet()) {
            batch.add(entry);
            if (batch.size() >= maxBatchSize) {
                break;
            }
        }
        if (batch.isEmpty()) {
            return;
        }

        Map<Integer, List<String>> groupedDeviceIds = new HashMap<>();
        for (Map.Entry<String, Integer> entry : batch) {
            groupedDeviceIds.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }

        int processed = batch.size();
        int success = 0;
        int failed = 0;
        int dropped = 0;
        for (Map.Entry<Integer, List<String>> group : groupedDeviceIds.entrySet()) {
            Integer status = group.getKey();
            List<String> deviceIds = group.getValue();
            try {
                int affected = deviceRepository.updateStatusByDeviceIds(deviceIds, status);
                dropped += Math.max(0, deviceIds.size() - affected);
                for (String deviceId : deviceIds) {
                    if (statusBuffer.remove(deviceId, status)) {
                        success++;
                    }
                }
            } catch (Exception ex) {
                failed += deviceIds.size();
                log.warn("Flush device status failed, status={}, batchSize={}", status, deviceIds.size(), ex);
            }
        }

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
            log.info("Flushed device status buffer, processed={}, success={}, failed={}, dropped={}, pending={}",
                    processed, success, failed, dropped, statusBuffer.size());
        }
    }
}
