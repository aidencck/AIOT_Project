package com.aiot.device.service.impl;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.dto.*;
import com.aiot.device.entity.Device;
import com.aiot.device.entity.DeviceCredential;
import com.aiot.device.entity.Product;
import com.aiot.device.mapper.DeviceCredentialMapper;
import com.aiot.device.mapper.DeviceMapper;
import com.aiot.device.mapper.ProductMapper;
import com.aiot.device.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    @Autowired
    private ProductMapper productMapper;
    
    @Autowired
    private DeviceMapper deviceMapper;
    
    @Autowired
    private DeviceCredentialMapper credentialMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${emqx.api.base-url}")
    private String emqxBaseUrl;

    @Value("${emqx.api.username}")
    private String emqxUsername;

    @Value("${emqx.api.password}")
    private String emqxPassword;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createProduct(ProductReq req) {
        Product product = new Product();
        String productId = "PRD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        product.setProductId(productId);
        product.setProductName(req.getName());
        product.setDescription(req.getDescription());
        product.setNodeType(req.getNodeType());
        product.setThingModelJson(req.getThingModelJson());
        
        product.setTenantId(1L); 
        
        productMapper.insert(product);
        return productId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceResp registerDevice(DeviceReq req) {
        Long count = productMapper.selectCount(
                new LambdaQueryWrapper<Product>().eq(Product::getProductId, req.getProductId())
        );
        if (count == null || count == 0) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }

        String deviceId = "DEV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setProductId(req.getProductId());
        device.setDeviceName(req.getDeviceName());
        device.setTenantId(1L);
        device.setStatus(0);
        deviceMapper.insert(device);

        String deviceSecret = UUID.randomUUID().toString().replace("-", "");
        DeviceCredential credential = new DeviceCredential();
        credential.setDeviceId(deviceId);
        credential.setDeviceSecret(deviceSecret);
        credential.setAuthType(0);
        credentialMapper.insert(credential);

        return new DeviceResp(deviceId, deviceSecret);
    }

    @Override
    public DeviceStatusResp getDeviceStatus(String deviceId) {
        Device device = deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceId, deviceId)
        );
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        
        // 1. Read real-time online status from Redis (Written by Auth Webhook)
        String redisKey = "aiot:device:status:" + deviceId;
        String statusStr = Boolean.TRUE.equals(redisTemplate.hasKey(redisKey)) ? "online" : "offline";
        
        // 2. Read shadow data (To be written by Data Parser)
        String shadowKey = "aiot:device:shadow:" + deviceId;
        Object shadowData = redisTemplate.opsForHash().entries(shadowKey);

        return new DeviceStatusResp(deviceId, statusStr, shadowData);
    }

    @Override
    public CommandResp sendCommand(String deviceId, CommandReq req) {
        Device device = deviceMapper.selectOne(
                new LambdaQueryWrapper<Device>().eq(Device::getDeviceId, deviceId)
        );
        if (device == null) {
            throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
        }
        
        // Check if device is online in Redis
        String redisKey = "aiot:device:status:" + deviceId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKey))) {
            throw new BusinessException(ResultCode.DEVICE_OFFLINE);
        }

        // Generate Message ID
        String msgId = "msg-" + UUID.randomUUID().toString();
        
        // Construct MQTT Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", msgId);
        payload.put("version", "1.0");
        payload.put("method", "thing.service." + req.getCommandName());
        payload.put("params", req.getParams());

        // Construct EMQX HTTP Publish API Request
        Map<String, Object> publishReq = new HashMap<>();
        publishReq.put("topic", "/sys/" + device.getProductId() + "/" + deviceId + "/thing/service/property/set");
        publishReq.put("payload", payload);
        publishReq.put("qos", 1);
        publishReq.put("clientid", "aiot-cloud-server");

        // Call EMQX API to push message to device
        try {
            WebClient webClient = WebClient.builder()
                    .baseUrl(emqxBaseUrl)
                    .defaultHeaders(header -> header.setBasicAuth(emqxUsername, emqxPassword))
                    .build();

            webClient.post()
                    .uri("/api/v5/publish")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(publishReq)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // Block for simplicity in MVP
            
            log.info("Successfully pushed command {} to device {}", req.getCommandName(), deviceId);
        } catch (Exception e) {
            log.error("Failed to push command to EMQX", e);
            throw new BusinessException("Failed to push command to device: " + e.getMessage());
        }

        return new CommandResp(msgId, "sent");
    }

    @Override
    public Object getTelemetry(String deviceId) {
        // MVP Mocking TSDB retrieval, real implementation requires TDengine driver
        return "[{\"ts\": 1690000000, \"temperature\": 24.5}, {\"ts\": 1690000060, \"temperature\": 25.0}]";
    }
}
