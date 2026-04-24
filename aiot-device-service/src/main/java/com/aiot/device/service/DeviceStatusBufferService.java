package com.aiot.device.service;

import com.aiot.device.mapper.DeviceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DeviceStatusBufferService {

    private final DeviceMapper deviceMapper;
    private final ConcurrentHashMap<String, Integer> statusBuffer = new ConcurrentHashMap<>();

    @Value("${aiot.device-status-buffer.max-batch-size:500}")
    private int maxBatchSize;

    public DeviceStatusBufferService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
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
        List<Map.Entry<String, Integer>> snapshot = new ArrayList<>(statusBuffer.entrySet());
        int processed = 0;
        int success = 0;
        for (Map.Entry<String, Integer> entry : snapshot) {
            if (processed >= maxBatchSize) {
                break;
            }
            processed++;
            String deviceId = entry.getKey();
            Integer status = entry.getValue();
            try {
                int affected = deviceMapper.updateStatusByDeviceId(deviceId, status);
                if (affected > 0) {
                    statusBuffer.remove(deviceId, status);
                    success++;
                } else {
                    statusBuffer.remove(deviceId, status);
                }
            } catch (Exception ex) {
                log.warn("Flush device status failed, deviceId={}, status={}", deviceId, status, ex);
            }
        }
        if (success > 0) {
            log.info("Flushed device status buffer, success={}, pending={}", success, statusBuffer.size());
        }
    }
}
