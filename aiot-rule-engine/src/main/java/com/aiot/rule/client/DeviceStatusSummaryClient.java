package com.aiot.rule.client;

import com.aiot.common.api.Result;
import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.common.http.CrossServiceHttpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class DeviceStatusSummaryClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;
    private final RestClient restClient;
    private final String internalToken;

    public DeviceStatusSummaryClient(
            CrossServiceHttpExecutor crossServiceHttpExecutor,
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${aiot.device-service.base-url:lb://aiot-device-service}") String deviceServiceBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
        this.internalToken = internalToken;
        this.restClient = restClientBuilder.clone().baseUrl(deviceServiceBaseUrl).build();
    }

    public DeviceStatusSummaryResp getStatusSummary() {
        try {
            Result<DeviceStatusSummaryResp> result = crossServiceHttpExecutor.executeBlocking(
                    "device-status-summary",
                    () -> restClient.get()
                            .uri("/api/v1/internal/devices/status/summary")
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Result<DeviceStatusSummaryResp>>() {})
            );
            if (result == null || result.getData() == null) {
                return emptySummary();
            }
            return result.getData();
        } catch (Exception ex) {
            log.warn("Failed to load device status summary from device-service, fallback to empty summary", ex);
            return emptySummary();
        }
    }

    private DeviceStatusSummaryResp emptySummary() {
        DeviceStatusSummaryResp summary = new DeviceStatusSummaryResp();
        summary.setOnlineCount(0L);
        summary.setOfflineCount(0L);
        summary.setTotalCount(0L);
        return summary;
    }
}
