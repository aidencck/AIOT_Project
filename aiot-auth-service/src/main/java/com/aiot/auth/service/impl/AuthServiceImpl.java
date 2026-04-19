package com.aiot.auth.service.impl;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;
import com.aiot.auth.entity.DeviceCredential;
import com.aiot.auth.mapper.DeviceCredentialMapper;
import com.aiot.auth.service.AuthService;
import com.aiot.auth.utils.SignUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private DeviceCredentialMapper credentialMapper;
    
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public boolean authenticateDevice(EmqxAuthReq req) {
        String deviceId = req.getUsername();
        String password = req.getPassword();
        
        log.info("Authenticating device: {}", deviceId);

        // 1. Fetch Secret from DB
        DeviceCredential credential = credentialMapper.selectOne(
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
            log.warn("Device [{}] signature mismatch. Expected: {}, Got: {}", deviceId, expectedPassword, password);
            return false;
        }
    }

    @Override
    public void handleDeviceStatusWebhook(EmqxWebhookReq req) {
        String deviceId = req.getUsername();
        String redisKey = "aiot:device:status:" + deviceId;
        
        if ("client.connected".equals(req.getAction())) {
            log.info("Webhook: Device [{}] is ONLINE", deviceId);
            // Set online status with a TTL (e.g., 2 minutes, assuming keep-alive is 60s)
            redisTemplate.opsForValue().set(redisKey, "online", 120, TimeUnit.SECONDS);
        } else if ("client.disconnected".equals(req.getAction())) {
            log.info("Webhook: Device [{}] is OFFLINE", deviceId);
            redisTemplate.delete(redisKey);
        }
    }
}
