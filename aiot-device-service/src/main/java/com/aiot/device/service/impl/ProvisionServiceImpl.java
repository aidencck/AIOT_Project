package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.DeviceCredential;
import com.aiot.device.repository.DeviceCredentialRepository;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.ProvisionService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Service
@Slf4j
public class ProvisionServiceImpl implements ProvisionService {

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceCredentialRepository credentialRepository;

    @Autowired
    private HomePermissionService homePermissionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aiot.mqtt.host:mqtt.aiot.com}")
    private String mqttHost;

    @Value("${aiot.mqtt.port:1883}")
    private Integer mqttPort;

    @Value("${aiot.provision.token-ttl-seconds:600}")
    private long tokenTtlSeconds;

    @Value("${aiot.provision.exchange-lock-seconds:10}")
    private long exchangeLockSeconds;

    @Value("${aiot.events.device-status-stream:aiot:stream:device-event}")
    private String deviceEventStream;

    @Override
    public String generateProvisionToken(String productKey, String deviceName, String homeId) {
        if (!StringUtils.hasText(productKey) || !StringUtils.hasText(deviceName)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "productKey 和 deviceName 不能为空");
        }
        homePermissionService.requireHomePermission(homeId, 2, "无权限为该家庭发放配网令牌");

        String auditId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = redisUtils.buildKey("device", "provision", token);
        ProvisionTokenPayload payload = new ProvisionTokenPayload(homeId, productKey, deviceName);
        redisUtils.set(redisKey, toJson(payload), tokenTtlSeconds, TimeUnit.SECONDS);
        log.info("Provision token issued, auditId={}, homeId={}, productKey={}, deviceName={}, tokenKey={}, ttlSeconds={}",
                auditId, homeId, productKey, deviceName, redisKey, tokenTtlSeconds);
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
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, null, req,
                    auditId, ResultCode.VALIDATE_FAILED.getCode(), "配网 Token 无效或已过期");
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "配网 Token 无效或已过期");
        }
        ProvisionTokenPayload payload;
        try {
            payload = parsePayload(tokenPayloadObj);
        } catch (BusinessException ex) {
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, null, req,
                    auditId, ex.getResultCode().getCode(), ex.getMessage());
            throw ex;
        }
        if (!Objects.equals(payload.productKey(), req.getProductKey())
                || !Objects.equals(payload.deviceName(), req.getDeviceName())) {
            log.warn("Provision token mismatch, auditId={}, expectedProductKey={}, reqProductKey={}, expectedDeviceName={}, reqDeviceName={}",
                    auditId, payload.productKey(), req.getProductKey(), payload.deviceName(), req.getDeviceName());
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, payload.homeId(), req,
                    auditId, ResultCode.FORBIDDEN.getCode(), "配网请求与令牌绑定信息不一致");
            throw new BusinessException(ResultCode.FORBIDDEN, "配网请求与令牌绑定信息不一致");
        }
        String lockKey = redisUtils.buildKey("device", "provision-lock", req.getProductKey() + ":" + req.getDeviceName());
        if (!redisUtils.setIfAbsent(lockKey, auditId, exchangeLockSeconds, TimeUnit.SECONDS)) {
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, payload.homeId(), req,
                    auditId, ResultCode.FAILED.getCode(), "设备配网处理中，请稍后重试");
            throw new BusinessException(ResultCode.FAILED, "设备配网处理中，请稍后重试");
        }
        try {
            ProvisionResp resp = resolveOrCreateProvisioning(payload, req, auditId);
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_SUCCEEDED, resp.getDeviceId(), payload.homeId(), req,
                    auditId, 200, "success");
            log.info("Provision exchange success, auditId={}, homeId={}, productKey={}, deviceName={}, deviceId={}",
                    auditId, payload.homeId(), req.getProductKey(), req.getDeviceName(), resp.getDeviceId());
            return resp;
        } catch (BusinessException ex) {
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, payload.homeId(), req,
                    auditId, ex.getResultCode().getCode(), ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_FAILED, null, payload.homeId(), req,
                    auditId, ResultCode.FAILED.getCode(), ex.getMessage());
            throw ex;
        } finally {
            redisUtils.delete(lockKey);
        }
    }

    private ProvisionResp resolveOrCreateProvisioning(ProvisionTokenPayload payload, ProvisionReq req, String auditId) {
        LambdaQueryWrapper<Device> existedQuery = new LambdaQueryWrapper<>();
        existedQuery.eq(Device::getProductKey, req.getProductKey())
                .eq(Device::getDeviceName, req.getDeviceName());
        List<Device> existedDevices = deviceRepository.selectList(existedQuery);
        Device existed = existedDevices.isEmpty() ? null : existedDevices.get(0);
        if (existedDevices.size() > 1) {
            log.warn("Provision duplicate device records detected, auditId={}, productKey={}, deviceName={}, records={}",
                    auditId, req.getProductKey(), req.getDeviceName(), existedDevices.size());
        }
        if (existed != null) {
            if (!Objects.equals(existed.getHomeId(), payload.homeId())) {
                log.warn("Provision home mismatch for existing device, auditId={}, productKey={}, deviceName={}, expectedHomeId={}, actualHomeId={}",
                        auditId, req.getProductKey(), req.getDeviceName(), payload.homeId(), existed.getHomeId());
                throw new BusinessException(ResultCode.FORBIDDEN, "设备已绑定其他家庭，禁止重复认领");
            }
            LambdaQueryWrapper<DeviceCredential> credentialQuery = new LambdaQueryWrapper<>();
            credentialQuery.eq(DeviceCredential::getDeviceId, existed.getId());
            DeviceCredential credential = credentialRepository.selectOne(credentialQuery);
            if (credential == null) {
                throw new BusinessException(ResultCode.FAILED, "设备凭证不存在，请联系管理员处理");
            }
            return buildResp(existed.getId(), credential.getDeviceSecret());
        }

        DeviceReq deviceReq = new DeviceReq();
        deviceReq.setProductKey(req.getProductKey());
        deviceReq.setDeviceName(req.getDeviceName());
        deviceReq.setHomeId(payload.homeId());

        DeviceResp deviceResp = deviceService.createDevice(deviceReq);
        return buildResp(deviceResp.getId(), deviceResp.getDeviceSecret());
    }

    private ProvisionResp buildResp(String deviceId, String deviceSecret) {
        ProvisionResp resp = new ProvisionResp();
        resp.setDeviceId(deviceId);
        resp.setDeviceSecret(deviceSecret);
        resp.setMqttHost(mqttHost);
        resp.setMqttPort(mqttPort);
        return resp;
    }

    private void publishProvisionEvent(DeviceEventType eventType,
                                       String deviceId,
                                       String homeId,
                                       ProvisionReq req,
                                       String auditId,
                                       int resultCode,
                                       String resultMessage) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("auditId", auditId);
        detail.put("homeId", homeId);
        detail.put("productKey", req.getProductKey());
        detail.put("deviceName", req.getDeviceName());
        detail.put("resultCode", resultCode);
        detail.put("resultMessage", resultMessage);
        DeviceEvent event = DeviceEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .deviceId(deviceId)
                .timestamp(System.currentTimeMillis())
                .source("aiot-device-service")
                .traceId(MDC.get("traceId"))
                .version(1L)
                .payload(detail)
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", event.getEventId());
            fields.put("eventType", eventType.name());
            fields.put("deviceId", deviceId == null ? "" : deviceId);
            fields.put("payload", payload);
            redisUtils.addToStream(deviceEventStream, fields);
        } catch (Exception ex) {
            log.warn("Failed to publish provision event, eventType={}, productKey={}, deviceName={}",
                    eventType, req.getProductKey(), req.getDeviceName(), ex);
        }
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
