package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.ProvisionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProvisionServiceImpl implements ProvisionService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private HomePermissionService homePermissionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aiot.mqtt.host:mqtt.aiot.com}")
    private String mqttHost;

    @Value("${aiot.mqtt.port:1883}")
    private Integer mqttPort;

    @Override
    public String generateProvisionToken(String productKey, String deviceName, String homeId, String authorizationHeader) {
        if (!StringUtils.hasText(productKey) || !StringUtils.hasText(deviceName)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "productKey 和 deviceName 不能为空");
        }
        homePermissionService.requireHomePermission(homeId, authorizationHeader, 2, "无权限为该家庭发放配网令牌");

        String auditId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = redisUtils.buildKey("device", "provision", token);
        ProvisionTokenPayload payload = new ProvisionTokenPayload(homeId, productKey, deviceName);
        redisUtils.set(redisKey, toJson(payload), 10, TimeUnit.MINUTES);
        log.info("Provision token issued, auditId={}, homeId={}, productKey={}, deviceName={}, tokenKey={}, ttlSeconds=600",
                auditId, homeId, productKey, deviceName, redisKey);
        return token;
    }

    @Override
    public ProvisionResp provisionDevice(ProvisionReq req) {
        if (req == null || !StringUtils.hasText(req.getProvisionToken())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "配网 Token 不能为空");
        }
        String auditId = UUID.randomUUID().toString();
        String redisKey = redisUtils.buildKey("device", "provision", req.getProvisionToken());
        Object tokenPayloadObj = redisUtils.getAndDelete(redisKey);
        if (tokenPayloadObj == null) {
            log.warn("Provision token invalid, auditId={}, tokenKey={}", auditId, redisKey);
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "配网 Token 无效或已过期");
        }

        ProvisionTokenPayload payload = parsePayload(tokenPayloadObj);
        if (!Objects.equals(payload.productKey(), req.getProductKey())
                || !Objects.equals(payload.deviceName(), req.getDeviceName())) {
            log.warn("Provision token mismatch, auditId={}, expectedProductKey={}, reqProductKey={}, expectedDeviceName={}, reqDeviceName={}",
                    auditId, payload.productKey(), req.getProductKey(), payload.deviceName(), req.getDeviceName());
            throw new BusinessException(ResultCode.FORBIDDEN, "配网请求与令牌绑定信息不一致");
        }

        // 1. 创建设备
        DeviceReq deviceReq = new DeviceReq();
        deviceReq.setProductKey(req.getProductKey());
        deviceReq.setDeviceName(req.getDeviceName());
        deviceReq.setHomeId(payload.homeId());
        
        DeviceResp deviceResp = deviceService.createDevice(deviceReq);

        // 2. 组装响应
        ProvisionResp resp = new ProvisionResp();
        resp.setDeviceId(deviceResp.getId());
        resp.setDeviceSecret(deviceResp.getDeviceSecret());
        resp.setMqttHost(mqttHost);
        resp.setMqttPort(mqttPort);
        log.info("Provision exchange success, auditId={}, homeId={}, productKey={}, deviceName={}, deviceId={}",
                auditId, payload.homeId(), req.getProductKey(), req.getDeviceName(), deviceResp.getId());

        return resp;
    }

    private String toJson(ProvisionTokenPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.FAILED, "配网令牌编码失败");
        }
    }

    private ProvisionTokenPayload parsePayload(Object payloadObj) {
        if (payloadObj instanceof String payloadStr) {
            try {
                return objectMapper.readValue(payloadStr, ProvisionTokenPayload.class);
            } catch (JsonProcessingException e) {
                throw new BusinessException(ResultCode.VALIDATE_FAILED, "配网 Token 数据格式错误");
            }
        }
        throw new BusinessException(ResultCode.VALIDATE_FAILED, "配网 Token 数据格式错误");
    }

    private record ProvisionTokenPayload(String homeId, String productKey, String deviceName) {
    }
}
