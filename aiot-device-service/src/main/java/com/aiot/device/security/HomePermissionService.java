package com.aiot.device.security;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
public class HomePermissionService {

    @Value("${aiot.home-service.base-url:http://127.0.0.1:8083}")
    private String homeServiceBaseUrl;

    public void requireHomePermission(String homeId, String authorizationHeader, int minRole, String denyMessage) {
        if (!StringUtils.hasText(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "homeId 不能为空");
        }
        if (!StringUtils.hasText(authorizationHeader) || !authorizationHeader.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少有效的 Authorization Header");
        }

        Result<Boolean> result = WebClient.builder()
                .baseUrl(homeServiceBaseUrl)
                .defaultHeader("Authorization", authorizationHeader)
                .build()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/homes/{homeId}/permission/check")
                        .queryParam("minRole", minRole)
                        .build(homeId))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Result<Boolean>>() {})
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(1, Duration.ofMillis(200)))
                .block();

        if (result == null || result.getCode() == null
                || !ResultCode.SUCCESS.getCode().equals(result.getCode())
                || !Boolean.TRUE.equals(result.getData())) {
            String message = StringUtils.hasText(denyMessage) ? denyMessage : "家庭权限不足";
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }
}
