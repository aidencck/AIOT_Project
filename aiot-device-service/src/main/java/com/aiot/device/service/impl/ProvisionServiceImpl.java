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
import com.aiot.device.entity.Product;
import com.aiot.device.repository.DeviceCredentialRepository;
import com.aiot.device.repository.DeviceRepository;
import com.aiot.device.repository.ProductRepository;
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
import org.springframework.dao.DuplicateKeyException;
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
    private ProductRepository productRepository;

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
    public String generateProvisionToken(String productKey, String deviceSn, String deviceName, String homeId) {
        String normalizedDeviceSn = normalizeOptionalValue(deviceSn);
        String normalizedDeviceName = normalizeOptionalValue(deviceName);
        if (!StringUtils.hasText(productKey) || (!StringUtils.hasText(normalizedDeviceSn) && !StringUtils.hasText(normalizedDeviceName))) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "productKey 和 deviceSn/deviceName 不能为空");
        }
        ensureProductExists(productKey);
        homePermissionService.requireHomePermission(homeId, 2, "无权限为该家庭发放配网令牌");

        String auditId = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = redisUtils.buildKey("device", "provision", token);
        ProvisionTokenPayload payload = new ProvisionTokenPayload(homeId, productKey, normalizedDeviceSn, normalizedDeviceName);
        redisUtils.set(redisKey, toJson(payload), tokenTtlSeconds, TimeUnit.SECONDS);
        log.info("Provision token issued, auditId={}, homeId={}, productKey={}, deviceSn={}, deviceName={}, tokenKey={}, ttlSeconds={}",
                auditId, homeId, productKey, normalizedDeviceSn, normalizedDeviceName, redisKey, tokenTtlSeconds);
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
        String requestDeviceSn = normalizeOptionalValue(req.getDeviceSn());
        String requestDeviceName = normalizeOptionalValue(req.getDeviceName());
        if (!isProvisionIdentityMatched(payload, req.getProductKey(), requestDeviceSn, requestDeviceName)) {
            log.warn("Provision token mismatch, auditId={}, expectedProductKey={}, reqProductKey={}, expectedDeviceSn={}, reqDeviceSn={}, expectedDeviceName={}, reqDeviceName={}",
                    auditId, payload.productKey(), req.getProductKey(), payload.deviceSn(), requestDeviceSn, payload.deviceName(), requestDeviceName);
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, payload.homeId(), req,
                    auditId, ResultCode.FORBIDDEN.getCode(), "配网请求与令牌绑定信息不一致");
            throw new BusinessException(ResultCode.FORBIDDEN, "配网请求与令牌绑定信息不一致");
        }
        String lockKey = redisUtils.buildKey("device", "provision-lock", buildProvisionIdentityKey(req.getProductKey(), requestDeviceSn, requestDeviceName));
        if (!redisUtils.setIfAbsentString(lockKey, auditId, exchangeLockSeconds, TimeUnit.SECONDS)) {
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_REJECTED, null, payload.homeId(), req,
                    auditId, ResultCode.FAILED.getCode(), "设备配网处理中，请稍后重试");
            throw new BusinessException(ResultCode.FAILED, "设备配网处理中，请稍后重试");
        }
        try {
            ProvisionResp resp = resolveOrCreateProvisioning(payload, req, auditId);
            publishProvisionEvent(DeviceEventType.DEVICE_PROVISION_SUCCEEDED, resp.getGlobalDeviceId(), payload.homeId(), req,
                    auditId, 200, "success");
            log.info("Provision exchange success, auditId={}, homeId={}, productKey={}, deviceSn={}, deviceName={}, deviceId={}",
                    auditId, payload.homeId(), req.getProductKey(), requestDeviceSn, requestDeviceName, resp.getGlobalDeviceId());
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
            redisUtils.releaseIfHeld(lockKey, auditId);
        }
    }

    private ProvisionResp resolveOrCreateProvisioning(ProvisionTokenPayload payload, ProvisionReq req, String auditId) {
        String deviceSn = normalizeOptionalValue(req.getDeviceSn());
        String deviceName = resolveProvisionDeviceName(payload, req);
        List<Device> existedDevices = findExistingDevices(req.getProductKey(), deviceSn, deviceName);
        Device existed = existedDevices.isEmpty() ? null : existedDevices.get(0);
        if (existedDevices.size() > 1) {
            log.warn("Provision duplicate device records detected, auditId={}, productKey={}, deviceSn={}, deviceName={}, records={}",
                    auditId, req.getProductKey(), deviceSn, deviceName, existedDevices.size());
            throw new BusinessException(ResultCode.FAILED, "设备唯一性被破坏，请联系管理员修复");
        }
        if (existed != null) {
            if (!StringUtils.hasText(existed.getHomeId())) {
                DeviceReq deviceReq = new DeviceReq();
                deviceReq.setProductKey(req.getProductKey());
                deviceReq.setDeviceName(deviceName);
                deviceReq.setGlobalDeviceId(req.getGlobalDeviceId());
                deviceReq.setDeviceSn(deviceSn);
                deviceReq.setAuthIdentity(req.getAuthIdentity());
                deviceReq.setHomeId(payload.homeId());
                DeviceResp deviceResp = deviceService.claimUnboundDevice(deviceReq);
                return buildResp(deviceResp.getId(), deviceResp.getGlobalDeviceId(),
                        deviceResp.getDeviceSn(), deviceResp.getAuthIdentity(), deviceResp.getDeviceSecret());
            }
            if (!Objects.equals(existed.getHomeId(), payload.homeId())) {
                log.warn("Provision home mismatch for existing device, auditId={}, productKey={}, deviceSn={}, deviceName={}, expectedHomeId={}, actualHomeId={}",
                        auditId, req.getProductKey(), deviceSn, deviceName, payload.homeId(), existed.getHomeId());
                throw new BusinessException(ResultCode.FORBIDDEN, "设备已绑定其他家庭，禁止重复认领");
            }
            mergeCompatibilityFields(existed, req, deviceName);
            return buildResp(existed.getId(), resolveGlobalDeviceId(existed),
                    existed.getDeviceSn(), resolveAuthIdentity(existed), loadCredential(existed.getId()).getDeviceSecret());
        }

        DeviceReq deviceReq = new DeviceReq();
        deviceReq.setProductKey(req.getProductKey());
        deviceReq.setDeviceName(deviceName);
        deviceReq.setGlobalDeviceId(req.getGlobalDeviceId());
        deviceReq.setDeviceSn(deviceSn);
        deviceReq.setAuthIdentity(req.getAuthIdentity());
        deviceReq.setHomeId(payload.homeId());

        try {
            DeviceResp deviceResp = deviceService.createDevice(deviceReq);
            return buildResp(deviceResp.getId(), deviceResp.getGlobalDeviceId(),
                    deviceResp.getDeviceSn(), deviceResp.getAuthIdentity(), deviceResp.getDeviceSecret());
        } catch (DuplicateKeyException ex) {
            List<Device> concurrentDevices = findExistingDevices(req.getProductKey(), deviceSn, deviceName);
            if (concurrentDevices.size() != 1) {
                throw new BusinessException(ResultCode.FAILED, "设备唯一性被破坏，请联系管理员修复");
            }
            Device concurrentDevice = concurrentDevices.get(0);
            if (!Objects.equals(concurrentDevice.getHomeId(), payload.homeId())) {
                throw new BusinessException(ResultCode.FORBIDDEN, "设备已绑定其他家庭，禁止重复认领");
            }
            mergeCompatibilityFields(concurrentDevice, req, deviceName);
            return buildResp(concurrentDevice.getId(), resolveGlobalDeviceId(concurrentDevice),
                    concurrentDevice.getDeviceSn(), resolveAuthIdentity(concurrentDevice),
                    loadCredential(concurrentDevice.getId()).getDeviceSecret());
        }
    }

    private ProvisionResp buildResp(String deviceId,
                                    String globalDeviceId,
                                    String deviceSn,
                                    String authIdentity,
                                    String deviceSecret) {
        ProvisionResp resp = new ProvisionResp();
        resp.setDeviceId(deviceId);
        resp.setGlobalDeviceId(globalDeviceId);
        resp.setDeviceSn(deviceSn);
        resp.setAuthIdentity(authIdentity);
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
        detail.put("deviceName", normalizeOptionalValue(req.getDeviceName()));
        detail.put("globalDeviceId", req.getGlobalDeviceId());
        detail.put("deviceSn", normalizeOptionalValue(req.getDeviceSn()));
        detail.put("authIdentity", req.getAuthIdentity());
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

    private List<Device> findExistingDevices(String productKey, String deviceSn, String deviceName) {
        String normalizedDeviceSn = normalizeOptionalValue(deviceSn);
        String normalizedDeviceName = normalizeOptionalValue(deviceName);
        if (StringUtils.hasText(normalizedDeviceSn)) {
            LambdaQueryWrapper<Device> bySnQuery = new LambdaQueryWrapper<>();
            bySnQuery.eq(Device::getDeviceSn, normalizedDeviceSn);
            List<Device> devices = deviceRepository.selectList(bySnQuery);
            if (!devices.isEmpty()) {
                ensureProductMatched(productKey, devices.get(0));
                return devices;
            }
        }
        if (!StringUtils.hasText(normalizedDeviceName)) {
            return List.of();
        }
        LambdaQueryWrapper<Device> existedQuery = new LambdaQueryWrapper<>();
        existedQuery.eq(Device::getProductKey, productKey)
                .eq(Device::getDeviceName, normalizedDeviceName);
        return deviceRepository.selectList(existedQuery);
    }

    private DeviceCredential loadCredential(String deviceId) {
        LambdaQueryWrapper<DeviceCredential> credentialQuery = new LambdaQueryWrapper<>();
        credentialQuery.eq(DeviceCredential::getDeviceId, deviceId);
        DeviceCredential credential = credentialRepository.selectOne(credentialQuery);
        if (credential == null) {
            throw new BusinessException(ResultCode.FAILED, "设备凭证不存在，请联系管理员处理");
        }
        return credential;
    }

    private void ensureProductExists(String productKey) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getProductKey, productKey);
        if (productRepository.selectOne(wrapper) == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND, "产品不存在");
        }
    }

    private String resolveGlobalDeviceId(Device device) {
        return device != null && StringUtils.hasText(device.getGlobalDeviceId())
                ? device.getGlobalDeviceId()
                : device == null ? null : device.getId();
    }

    private void mergeCompatibilityFields(Device device, ProvisionReq req, String resolvedDeviceName) {
        if (device == null || req == null) {
            return;
        }
        boolean changed = false;
        boolean shouldBackfillDeviceName = req.getDeviceName() != null || !StringUtils.hasText(device.getDeviceName());
        if (shouldBackfillDeviceName
                && StringUtils.hasText(resolvedDeviceName)
                && !Objects.equals(device.getDeviceName(), resolvedDeviceName)) {
            device.setDeviceName(resolvedDeviceName);
            changed = true;
        }
        if (req.getGlobalDeviceId() != null) {
            String globalDeviceId = StringUtils.hasText(req.getGlobalDeviceId()) ? req.getGlobalDeviceId() : device.getId();
            if (!Objects.equals(device.getGlobalDeviceId(), globalDeviceId)) {
                device.setGlobalDeviceId(globalDeviceId);
                changed = true;
            }
        } else if (!StringUtils.hasText(device.getGlobalDeviceId())) {
            device.setGlobalDeviceId(device.getId());
            changed = true;
        }
        if (req.getDeviceSn() != null) {
            String deviceSn = StringUtils.hasText(req.getDeviceSn()) ? req.getDeviceSn() : null;
            if (!Objects.equals(device.getDeviceSn(), deviceSn)) {
                device.setDeviceSn(deviceSn);
                changed = true;
            }
        }
        if (req.getAuthIdentity() != null) {
            String authIdentity = StringUtils.hasText(req.getAuthIdentity())
                    ? req.getAuthIdentity()
                    : resolveAuthIdentity(device);
            if (!Objects.equals(device.getAuthIdentity(), authIdentity)) {
                device.setAuthIdentity(authIdentity);
                changed = true;
            }
        } else if (!StringUtils.hasText(device.getAuthIdentity())) {
            device.setAuthIdentity(resolveAuthIdentity(device));
            changed = true;
        }
        if (changed) {
            deviceRepository.updateById(device);
        }
    }

    private boolean isProvisionIdentityMatched(ProvisionTokenPayload payload,
                                               String productKey,
                                               String deviceSn,
                                               String deviceName) {
        if (!Objects.equals(payload.productKey(), productKey)) {
            return false;
        }
        String expectedDeviceSn = normalizeOptionalValue(payload.deviceSn());
        if (StringUtils.hasText(expectedDeviceSn)) {
            return Objects.equals(expectedDeviceSn, normalizeOptionalValue(deviceSn));
        }
        String expectedDeviceName = normalizeOptionalValue(payload.deviceName());
        return Objects.equals(expectedDeviceName, normalizeOptionalValue(deviceName));
    }

    private String buildProvisionIdentityKey(String productKey, String deviceSn, String deviceName) {
        String normalizedDeviceSn = normalizeOptionalValue(deviceSn);
        if (StringUtils.hasText(normalizedDeviceSn)) {
            return productKey + ":sn:" + normalizedDeviceSn;
        }
        return productKey + ":name:" + normalizeOptionalValue(deviceName);
    }

    private String resolveProvisionDeviceName(ProvisionTokenPayload payload, ProvisionReq req) {
        String resolved = firstNonBlank(
                normalizeOptionalValue(req.getDeviceName()),
                normalizeOptionalValue(payload.deviceName()),
                normalizeOptionalValue(req.getDeviceSn()),
                normalizeOptionalValue(payload.deviceSn())
        );
        if (!StringUtils.hasText(resolved)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "deviceSn 或 deviceName 至少传一个");
        }
        return resolved;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String normalizeOptionalValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void ensureProductMatched(String productKey, Device device) {
        if (device != null && !Objects.equals(device.getProductKey(), productKey)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "设备产品与配网请求不匹配");
        }
    }

    private String resolveAuthIdentity(Device device) {
        if (device == null) {
            return null;
        }
        if (StringUtils.hasText(device.getAuthIdentity())) {
            return device.getAuthIdentity();
        }
        if (StringUtils.hasText(device.getDeviceSn())) {
            return device.getDeviceSn();
        }
        return resolveGlobalDeviceId(device);
    }

    private record ProvisionTokenPayload(String homeId, String productKey, String deviceSn, String deviceName) {
    }
}
