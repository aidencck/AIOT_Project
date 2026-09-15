package com.aiot.rule.client;

import com.aiot.common.api.Result;
import com.aiot.common.dto.ai.AiDeviceContextResp;
import com.aiot.common.dto.ai.AiRuntimeContextPayload;
import com.aiot.common.http.CrossServiceHttpExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class AiDeviceContextClient implements AiContextProvider {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final RestClient restClient;
    private final String deviceServiceBaseUrl;
    private final String internalToken;
    private final CrossServiceHttpExecutor executor;

    public AiDeviceContextClient(
            RestClient.Builder restClientBuilder,
            @org.springframework.beans.factory.annotation.Value("${aiot.device-service.base-url:lb://aiot-device-service}") String deviceServiceBaseUrl,
            @org.springframework.beans.factory.annotation.Value("${aiot.internal.token:${AIOT_INTERNAL_TOKEN:}}") String internalToken,
            CrossServiceHttpExecutor executor) {
        this.deviceServiceBaseUrl = deviceServiceBaseUrl;
        this.internalToken = internalToken;
        this.restClient = restClientBuilder.clone().baseUrl(deviceServiceBaseUrl).build();
        this.executor = executor;
    }

    @Override
    public AiRuntimeContextPayload getRuntimeContext(String deviceId, String sceneType) {
        AiRuntimeContextPayload payload = getRuntimeContextPayload(deviceId, sceneType);
        if (payload != null) {
            return payload;
        }
        return AiRuntimeContextPayload.fromLegacy(getDeviceContext(deviceId, sceneType));
    }

    private AiRuntimeContextPayload getRuntimeContextPayload(String deviceId, String sceneType) {
        try {
            return executor.executeBlocking("rule-device-ai-context", () -> {
                Result<AiRuntimeContextPayload> result = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/ai/devices/{deviceId}/runtime-context")
                                .queryParam("sceneType", sceneType)
                                .build(deviceId))
                        .header(INTERNAL_TOKEN_HEADER, internalToken)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Result<AiRuntimeContextPayload>>() {
                        });
                return result == null ? null : result.getData();
            });
        } catch (Exception ex) {
            log.info("Falling back to legacy AI context endpoint from {}, deviceId={}, sceneType={}",
                    deviceServiceBaseUrl, deviceId, sceneType);
            return null;
        }
    }

    public AiDeviceContextResp getDeviceContext(String deviceId, String sceneType) {
        try {
            return executor.executeBlocking("rule-device-ai-context", () -> {
                Result<AiDeviceContextResp> result = restClient.get()
                        .uri(uriBuilder -> uriBuilder.path("/api/v1/internal/ai/devices/{deviceId}/context")
                                .queryParam("sceneType", sceneType)
                                .build(deviceId))
                        .header(INTERNAL_TOKEN_HEADER, internalToken)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Result<AiDeviceContextResp>>() {
                        });
                return result == null ? null : result.getData();
            });
        } catch (Exception ex) {
            log.warn("Failed to load AI device context from {}, deviceId={}, sceneType={}",
                    deviceServiceBaseUrl, deviceId, sceneType, ex);
            return null;
        }
    }
}
