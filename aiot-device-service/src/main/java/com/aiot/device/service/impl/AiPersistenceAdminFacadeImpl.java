package com.aiot.device.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.dto.ai.AiBusinessLiveFlowSnapshot;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.device.service.AiPersistenceAdminFacade;
import com.aiot.common.http.CrossServiceHttpExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Service
public class AiPersistenceAdminFacadeImpl implements AiPersistenceAdminFacade {

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;
    private final WebClient.Builder webClientBuilder;
    private final @NonNull String ruleServiceBaseUrl;

    public AiPersistenceAdminFacadeImpl(CrossServiceHttpExecutor crossServiceHttpExecutor,
                                        WebClient.Builder webClientBuilder,
                                        @Value("${aiot.rule-service.base-url:lb://aiot-rule-engine}") @NonNull String ruleServiceBaseUrl) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
        this.webClientBuilder = webClientBuilder;
        this.ruleServiceBaseUrl = Objects.requireNonNull(ruleServiceBaseUrl, "ruleServiceBaseUrl must not be null");
    }

    @Override
    public AiPersistenceAdminQueryResp query() {
        Result<AiPersistenceAdminQueryResp> result = crossServiceHttpExecutor.execute(
                "device-admin-rule-ai-persistence-query",
                () -> webClientBuilder
                        .clone()
                        .baseUrl(ruleServiceBaseUrl)
                        .build()
                        .get()
                        .uri("/api/v1/admin/ai/persistence/query")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<AiPersistenceAdminQueryResp>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return AiPersistenceAdminQueryResp.builder().build();
        }
        return result.getData();
    }

    @Override
    public AiBusinessLiveFlowSnapshot getBusinessLiveFlow() {
        AiPersistenceAdminQueryResp response = query();
        return response == null ? null : response.getBusinessLiveFlow();
    }

    @Override
    public AiPersistenceHistoryDetailResp getHistoryDetail(String reportType, Long occurredAt) {
        Result<AiPersistenceHistoryDetailResp> result = crossServiceHttpExecutor.execute(
                "device-admin-rule-ai-persistence-history-detail-" + reportType + "-" + occurredAt,
                () -> webClientBuilder
                        .clone()
                        .baseUrl(ruleServiceBaseUrl)
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/admin/ai/persistence/history/detail")
                                .queryParam("reportType", reportType)
                                .queryParam("occurredAt", occurredAt)
                                .build())
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<AiPersistenceHistoryDetailResp>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return AiPersistenceHistoryDetailResp.builder()
                    .reportType(reportType)
                    .occurredAt(occurredAt)
                    .exists(Boolean.FALSE)
                    .content(java.util.Map.of())
                    .build();
        }
        return result.getData();
    }
}
