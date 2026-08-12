package com.aiot.device.security;

import com.aiot.common.api.Result;
import com.aiot.common.api.ResultCode;
import com.aiot.common.dto.home.HomeRoomRelationCheckResp;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.http.CrossServiceHttpExecutor;
import com.aiot.device.utils.UserContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class HomePermissionService {

    private final CrossServiceHttpExecutor crossServiceHttpExecutor;

    public HomePermissionService(CrossServiceHttpExecutor crossServiceHttpExecutor) {
        this.crossServiceHttpExecutor = crossServiceHttpExecutor;
    }

    @Value("${aiot.home-service.base-url:http://127.0.0.1:8083}")
    private String homeServiceBaseUrl;

    @Value("${aiot.internal.token:}")
    private String internalToken;

    public void requireHomePermission(String homeId, int minRole, String denyMessage) {
        if (!StringUtils.hasText(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "homeId 不能为空");
        }
        UserContext.UserInfo userInfo = UserContext.get();
        if (userInfo == null || !StringUtils.hasText(userInfo.getUserId())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "缺少用户身份上下文");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "内部调用密钥未配置");
        }
        if (!StringUtils.hasText(homeServiceBaseUrl)) {
            throw new BusinessException(ResultCode.FAILED, "家庭服务地址未配置");
        }
        String baseUrl = homeServiceBaseUrl;

        Result<Boolean> result = crossServiceHttpExecutor.execute(
                "device-home-permission",
                () -> WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Token", internalToken)
                        .defaultHeader("X-User-Id", userInfo.getUserId())
                        .defaultHeader("X-Global-User-Id", userInfo.getGlobalUserId())
                        .defaultHeader("X-User-Phone", StringUtils.hasText(userInfo.getPhone()) ? userInfo.getPhone() : "")
                        .build()
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/api/v1/internal/homes/{homeId}/permission/check")
                                .queryParam("minRole", minRole)
                                .build(homeId))
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<Boolean>>() {})
        );

        if (result == null || result.getCode() == null
                || !ResultCode.SUCCESS.getCode().equals(result.getCode())
                || !Boolean.TRUE.equals(result.getData())) {
            String message = StringUtils.hasText(denyMessage) ? denyMessage : "家庭权限不足";
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    public HomeRoomRelationCheckResp checkHomeRoomRelation(String homeId, String roomId) {
        if (!StringUtils.hasText(homeId)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "homeId 不能为空");
        }
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "内部调用密钥未配置");
        }
        if (!StringUtils.hasText(homeServiceBaseUrl)) {
            throw new BusinessException(ResultCode.FAILED, "家庭服务地址未配置");
        }
        String baseUrl = homeServiceBaseUrl;

        Result<HomeRoomRelationCheckResp> result = crossServiceHttpExecutor.execute(
                "device-home-relation",
                () -> WebClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader("X-Internal-Token", internalToken)
                        .build()
                        .get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/api/v1/internal/homes/{homeId}/relation/check");
                            if (StringUtils.hasText(roomId)) {
                                builder.queryParam("roomId", roomId);
                            }
                            return builder.build(homeId);
                        })
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<Result<HomeRoomRelationCheckResp>>() {})
        );
        if (result == null || result.getCode() == null
                || !ResultCode.SUCCESS.getCode().equals(result.getCode())
                || result.getData() == null) {
            throw new BusinessException(ResultCode.FAILED, "家庭空间关系校验失败");
        }
        return result.getData();
    }
}
