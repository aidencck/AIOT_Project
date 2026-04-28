package com.aiot.auth.service.impl;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;
import com.aiot.auth.entity.DeviceCredential;
import com.aiot.auth.repository.DeviceCredentialRepository;
import com.aiot.auth.service.AuthService;
import com.aiot.auth.utils.SignUtils;
import com.aiot.common.config.RedisUtils;
import com.aiot.common.event.DeviceEvent;
import com.aiot.common.event.DeviceEventType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {
    private static final String WEBHOOK_REPLAY_KEY_PREFIX = "aiot:auth:webhook:replay:";

    @Autowired
    private DeviceCredentialRepository credentialRepository;
    
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${aiot.emqx.webhook.secret:}")
    private String webhookSecret;

    @Value("${aiot.emqx.webhook.max-skew-seconds:300}")
    private long webhookMaxSkewSeconds;

    @Value("${aiot.events.device-status-stream:aiot:stream:device-event}")
    private String deviceStatusStream;

    @Override
    public boolean authenticateDevice(EmqxAuthReq req) {
        String deviceId = req.getUsername();
        String password = req.getPassword();
        
        log.info("Authenticating device: {}", deviceId);

        // 1. Fetch Secret from DB
        DeviceCredential credential = credentialRepository.selectOne(
                new LambdaQueryWrapper<DeviceCredential>().eq(DeviceCredential::getDeviceId, deviceId)
        );
        
        if (credential == null) {
            log.warn("Device [{}] credential not found", deviceId);
            return false;
        }

        // 2. Verify Signature: Expected Password = HmacSHA256(clientId, deviceSecret)
        String expectedPassword = SignUtils.signWithHmacSha256(req.getClientid(), credential.getDeviceSecret());
        
        if (expectedPassword.equals(password)) {
            log.info("Device [{}] authenticated successfully", deviceId);
            return true;
        } else {
            log.warn("Device [{}] signature mismatch", deviceId);
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String action, String clientId, String username, Long timestamp, String signatureHeader) {
        if (!StringUtils.hasText(webhookSecret)) {
            log.warn("Webhook secret not configured, deny webhook by default");
            return false;
        }
        if (!StringUtils.hasText(signatureHeader) || timestamp == null) {
            return false;
        }

        long nowSeconds = System.currentTimeMillis() / 1000;
        if (Math.abs(nowSeconds - timestamp) > webhookMaxSkewSeconds) {
            log.warn("Webhook timestamp out of range, ts={}, now={}", timestamp, nowSeconds);
            return false;
        }

        String payload = String.format("%s.%s.%s.%d",
                action == null ? "" : action,
                clientId == null ? "" : clientId,
                username == null ? "" : username,
                timestamp);
        String expected = SignUtils.signWithHmacSha256(payload, webhookSecret);
        boolean signatureValid = java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
        );
        if (!signatureValid) {
            return false;
        }

        // Use signature as idempotency fingerprint to block replay in allowed skew window.
        String replayKey = WEBHOOK_REPLAY_KEY_PREFIX + signatureHeader;
        boolean firstSeen = redisUtils.setIfAbsent(
                replayKey,
                "1",
                webhookMaxSkewSeconds,
                TimeUnit.SECONDS
        );
        if (!firstSeen) {
            log.warn("Detected replay webhook, clientId={}, username={}, timestamp={}",
                    clientId, username, timestamp);
            return false;
        }
        return true;
    }

    @Override
    public void handleDeviceStatusWebhook(EmqxWebhookReq req) {
        String deviceId = req.getUsername();
        String redisKey = redisUtils.buildKey("device", "status", deviceId);
        
        if ("client.connected".equals(req.getAction())) {
            log.info("Webhook: Device [{}] is ONLINE", deviceId);
            // Set online status with a TTL (e.g., 120 seconds)
            redisUtils.set(redisKey, "online", 120, TimeUnit.SECONDS);
            publishDeviceEvent(deviceId, DeviceEventType.DEVICE_ONLINE, req.getAction(), req.getTimestamp());
        } else if ("client.disconnected".equals(req.getAction())) {
            log.info("Webhook: Device [{}] is OFFLINE", deviceId);
            redisUtils.delete(redisKey);
            publishDeviceEvent(deviceId, DeviceEventType.DEVICE_OFFLINE, req.getAction(), req.getTimestamp());
        }
    }

    private void publishDeviceEvent(String deviceId, DeviceEventType eventType, String action, Long timestamp) {
        long eventTimestamp = timestamp == null ? System.currentTimeMillis() : timestamp * 1000;
        String eventId = String.format("%s:%s:%d", deviceId, action, eventTimestamp);
        DeviceEvent event = DeviceEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .deviceId(deviceId)
                .timestamp(eventTimestamp)
                .source("aiot-auth-service")
                .traceId(MDC.get("traceId"))
                .build();
        try {
            String payload = objectMapper.writeValueAsString(event);
            Map<String, String> fields = new HashMap<>();
            fields.put("eventId", eventId);
            fields.put("eventType", eventType.name());
            fields.put("deviceId", deviceId);
            fields.put("payload", payload);
            redisUtils.addToStream(deviceStatusStream, fields);
            log.info("Published device event to stream={}, eventType={}, deviceId={}",
                    deviceStatusStream, eventType, deviceId);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize device event, eventType={}, deviceId={}", eventType, deviceId, e);
        } catch (Exception e) {
            log.warn("Failed to publish device event, eventType={}, deviceId={}", eventType, deviceId, e);
        }
    }
}
