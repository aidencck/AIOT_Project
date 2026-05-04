package com.aiot.home.service;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HomeDeviceCompensationService {

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;

    public HomeDeviceCompensationService(CrossServiceHttpExecutor crossServiceHttpExecutor) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
    }

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
        if (!StringUtils.hasText(deviceServiceBaseUrl)) {
            throw new BusinessException(ResultCode.FAILED, "设备服务地址未配置");
        }
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "补偿目标标识不能为空");
        }
        String baseUrl = deviceServiceBaseUrl;

        String auditId = UUID.randomUUID().toString();
        log.info("Compensation start, auditId={}, action={}, target={}", auditId, action, value);
        try {
            Result<Boolean> result = crossServiceHttpExecutor.execute(
                    "home-device-compensation",
                    () -> WebClient.builder()
                            .baseUrl(baseUrl)
                            .defaultHeader("X-Internal-Token", internalToken)
                            .build()
                            .post()
                            .uri(path, value)
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Result<Boolean>>() {})
            );
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
