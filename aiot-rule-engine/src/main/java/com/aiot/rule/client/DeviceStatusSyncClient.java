package com.aiot.rule.client;

import com.aiot.common.http.CrossServiceHttpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DeviceStatusSyncClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;
    private final RestClient restClient;
    private final String internalToken;

    public DeviceStatusSyncClient(
            CrossServiceHttpExecutor crossServiceHttpExecutor,
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${aiot.device-service.base-url:lb://aiot-device-service}") String deviceServiceBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
        this.internalToken = internalToken;
        this.restClient = restClientBuilder.clone().baseUrl(deviceServiceBaseUrl).build();
    }

    public void syncStatus(String deviceId, int status) {
        try {
            crossServiceHttpExecutor.executeBlocking(
                    "device-status-sync",
                    () -> {
                        restClient.post()
                                .uri("/api/v1/internal/devices/{deviceId}/status?status={status}", deviceId, status)
                                .header(INTERNAL_TOKEN_HEADER, internalToken)
                                .retrieve()
                                .toBodilessEntity();
                        return null;
                    }
            );
        } catch (Exception ex) {
            log.warn("Failed to sync device status to device-service, deviceId={}, status={}", deviceId, status, ex);
        }
    }
}
