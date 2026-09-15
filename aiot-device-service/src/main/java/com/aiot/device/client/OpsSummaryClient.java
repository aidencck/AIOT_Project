package com.aiot.device.client;

import com.aiot.common.api.Result;
import com.aiot.common.http.CrossServiceHttpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class OpsSummaryClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;
    private final CrossServiceHttpExecutor executor;

    public OpsSummaryClient(
            RestClient.Builder restClientBuilder,
            @Value("${aiot.rule-engine.base-url:lb://aiot-rule-engine}") String ruleEngineBaseUrl,
            @Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken,
            CrossServiceHttpExecutor executor) {
        this.restClient = restClientBuilder.clone().baseUrl(ruleEngineBaseUrl).build();
        this.internalToken = internalToken;
        this.executor = executor;
    }

    public Map<String, Object> getDeviceOpsSummary(String deviceIdentity) {
        try {
            Result<Map<String, Object>> result = executor.executeBlocking(
                    "device-ops-summary",
                    () -> restClient.get()
                            .uri("/api/v1/internal/ops/devices/{deviceIdentity}/summary", deviceIdentity)
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .retrieve()
                            .body(new ParameterizedTypeReference<Result<Map<String, Object>>>() {
                            }));
            if (result == null || result.getData() == null) {
                log.warn("设备运维汇总为空, deviceIdentity={}", deviceIdentity);
                return Collections.emptyMap();
            }
            return result.getData();
        } catch (Exception ex) {
            log.warn("查询设备运维汇总失败, deviceIdentity={}", deviceIdentity, ex);
            return Collections.emptyMap();
        }
    }
}
