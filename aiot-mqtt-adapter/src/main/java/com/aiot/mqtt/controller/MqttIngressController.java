package com.aiot.mqtt.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.common.security.InternalTokenUtils;
import com.aiot.mqtt.dto.MqttDispatchResponse;
import com.aiot.mqtt.dto.MqttIngressRequest;
import com.aiot.mqtt.service.MqttIngressService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mqtt")
public class MqttIngressController {

    private final MqttIngressService mqttIngressService;
    private final String internalToken;

    public MqttIngressController(
            MqttIngressService mqttIngressService,
            @Value("${aiot.internal.token:}") String internalToken) {
        this.mqttIngressService = mqttIngressService;
        this.internalToken = internalToken;
    }

    @PostMapping("/messages")
    public MqttDispatchResponse ingest(
            @RequestBody MqttIngressRequest request,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        verifyInternalToken(token);
        return mqttIngressService.dispatchToParser(request);
    }

    private void verifyInternalToken(String token) {
        if (!StringUtils.hasText(internalToken)) {
            throw new BusinessException(ResultCode.FAILED, "服务内部令牌未配置");
        }
        if (!InternalTokenUtils.matches(internalToken, token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "非法入站调用");
        }
    }
}
