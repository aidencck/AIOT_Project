package com.aiot.home.service;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HomeDeviceCompensationService {

    @Value("${aiot.device-service.base-url:http://127.0.0.1:8081}")
    private String deviceServiceBaseUrl;

    @Value("${aiot.internal.token:}")
    private String internalToken;

    public void unbindDevicesByHomeId(String homeId) {
        invokeCompensation("UNBIND_BY_HOME", "/api/v1/internal/devices/unbind/home/{homeId}", homeId);
    }

    public void unbindDevicesByRoomId(String roomId) {
        invokeCompensation("UNBIND_BY_ROOM", "/api/v1/internal/devices/unbind/room/{roomId}", roomId);
    }

    private void invokeCompensation(String action, String path, String value) {
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.FAILED, "缺少内部通信令牌，无法执行跨服务补偿");
        }
        String auditId = UUID.randomUUID().toString();
        log.info("Compensation start, auditId={}, action={}, target={}", auditId, action, value);
        try {
            Result<Boolean> result = WebClient.builder()
                    .baseUrl(deviceServiceBaseUrl)
                    .defaultHeader("X-Internal-Token", internalToken)
                    .build()
                    .post()
                    .uri(path, value)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Result<Boolean>>() {})
                    .timeout(Duration.ofSeconds(2))
                    .retryWhen(Retry.backoff(1, Duration.ofMillis(200)))
                    .block();
            if (result == null || result.getCode() == null
                    || !ResultCode.SUCCESS.getCode().equals(result.getCode())
                    || !Boolean.TRUE.equals(result.getData())) {
                log.warn("Compensation failed, auditId={}, action={}, target={}, result={}",
                        auditId, action, value, result);
                throw new BusinessException(ResultCode.FAILED, "跨服务补偿执行失败");
            }
            log.info("Compensation success, auditId={}, action={}, target={}", auditId, action, value);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Compensation request error, auditId={}, action={}, target={}", auditId, action, value, e);
            throw new BusinessException(ResultCode.FAILED, "跨服务补偿调用失败");
        }
    }
}
