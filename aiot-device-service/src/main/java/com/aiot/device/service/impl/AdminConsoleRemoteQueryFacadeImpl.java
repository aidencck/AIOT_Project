package com.aiot.device.service.impl;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.device.dto.AdminAiEvalReportResp;
import com.aiot.device.service.AdminConsoleRemoteQueryFacade;
import com.aiot.device.utils.UserContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.List;

@Service
public class AdminConsoleRemoteQueryFacadeImpl implements AdminConsoleRemoteQueryFacade {

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;
    private final WebClient.Builder webClientBuilder;
    private final @NonNull String homeServiceBaseUrl;
    private final @NonNull String ruleServiceBaseUrl;

    public AdminConsoleRemoteQueryFacadeImpl(CrossServiceHttpExecutor crossServiceHttpExecutor,
                                             WebClient.Builder webClientBuilder,
                                             @Value("${aiot.home-service.base-url:lb://aiot-home-service}") @NonNull String homeServiceBaseUrl,
                                             @Value("${aiot.rule-service.base-url:lb://aiot-rule-engine}") @NonNull String ruleServiceBaseUrl) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
        this.webClientBuilder = webClientBuilder;
        this.homeServiceBaseUrl = Objects.requireNonNull(homeServiceBaseUrl, "homeServiceBaseUrl must not be null");
        this.ruleServiceBaseUrl = Objects.requireNonNull(ruleServiceBaseUrl, "ruleServiceBaseUrl must not be null");
    }

    @Override
    public List<Map<String, Object>> loadHomes(String authorizationHeader) {
        Result<List<Map<String, Object>>> result = crossServiceHttpExecutor.execute(
                "device-admin-home-list",
                () -> withUserIdentity(
                        webClientBuilder.clone()
                                .baseUrl(homeServiceBaseUrl)
                                .defaultHeader("Authorization", authorizationHeader))
                        .build()
                        .get()
                        .uri("/api/v1/homes")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            throw new BusinessException(ResultCode.FAILED, "拉取家庭列表失败");
        }
        return result.getData();
    }

    @Override
    public List<Map<String, Object>> loadHomeMembers(String homeId, String authorizationHeader) {
        Result<List<Map<String, Object>>> result = crossServiceHttpExecutor.execute(
                "device-admin-home-members",
                () -> withUserIdentity(
                        webClientBuilder.clone()
                                .baseUrl(homeServiceBaseUrl)
                                .defaultHeader("Authorization", authorizationHeader))
                        .build()
                        .get()
                        .uri("/api/v1/homes/{homeId}/members", homeId)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<List<Map<String, Object>>>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return Collections.emptyList();
        }
        return result.getData();
    }

    @Override
    public Map<String, Object> loadOpsOverview() {
        Result<Map<String, Object>> result = crossServiceHttpExecutor.execute(
                "device-admin-rule-overview",
                () -> webClientBuilder
                        .clone()
                        .baseUrl(ruleServiceBaseUrl)
                        .build()
                        .get()
                        .uri("/api/v1/admin/dashboard/overview")
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return Collections.emptyMap();
        }
        return result.getData();
    }

    @Override
    public AdminAiEvalReportResp loadAiEvalRegressionGate(String sceneType) {
        Result<Map<String, Object>> result = crossServiceHttpExecutor.execute(
                "device-admin-rule-ai-eval-" + sceneType,
                () -> webClientBuilder
                        .clone()
                        .baseUrl(ruleServiceBaseUrl)
                        .build()
                        .get()
                        .uri("/api/v1/admin/ai/evals/{sceneType}/regression-gate", sceneType)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<Map<String, Object>>>() {})
        );
        if (result == null || !ResultCode.SUCCESS.getCode().equals(result.getCode()) || result.getData() == null) {
            return AdminAiEvalReportResp.builder()
                    .sceneType(sceneType)
                    .reportType("regression_gate")
                    .exists(Boolean.FALSE)
                    .gatePassed(Boolean.FALSE)
                    .content(Collections.emptyMap())
                    .build();
        }
        Map<String, Object> data = result.getData();
        Map<String, Object> content = extractContent(data);
        return AdminAiEvalReportResp.builder()
                .sceneType(stringValue(data.get("sceneType"), sceneType))
                .reportType(stringValue(data.get("reportType"), "regression_gate"))
                .reportPath(nullableString(firstNonNull(data.get("reportPath"), content.get("reportPath"))))
                .exists(booleanValue(data.get("exists")))
                .gatePassed(booleanValue(firstNonNull(data.get("gatePassed"), content.get("gatePassed"))))
                .generatedAt(longValue(data.get("generatedAt")))
                .content(content)
                .build();
    }

    private WebClient.Builder withUserIdentity(WebClient.Builder builder) {
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null) {
            return builder;
        }
        if (StringUtils.hasText(userInfo.getUserId())) {
            builder.defaultHeader("X-User-Id", userInfo.getUserId());
        }
        if (StringUtils.hasText(userInfo.getGlobalUserId())) {
            builder.defaultHeader("X-Global-User-Id", userInfo.getGlobalUserId());
        }
        if (StringUtils.hasText(userInfo.getPhone())) {
            builder.defaultHeader("X-User-Phone", userInfo.getPhone());
        }
        return builder;
    }

    private Map<String, Object> extractContent(Map<String, Object> data) {
        Map<String, Object> content = toStringObjectMap(data.get("content"));
        if (!content.isEmpty()) {
            return content;
        }
        Map<String, Object> fallback = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (isEvalEnvelopeField(entry.getKey())) {
                continue;
            }
            fallback.put(entry.getKey(), entry.getValue());
        }
        return fallback;
    }

    private boolean isEvalEnvelopeField(String key) {
        return "sceneType".equals(key)
                || "reportType".equals(key)
                || "reportPath".equals(key)
                || "exists".equals(key)
                || "gatePassed".equals(key)
                || "generatedAt".equals(key);
    }

    private Map<String, Object> toStringObjectMap(Object value) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                content.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return content;
    }

    private Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
