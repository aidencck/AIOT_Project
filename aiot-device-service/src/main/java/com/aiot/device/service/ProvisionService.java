package com.aiot.device.service;

import com.aiot.device.dto.ProvisionReq;
import com.aiot.device.dto.ProvisionResp;

public interface ProvisionService {
    /**
     * 生成设备的配网 Token，并存入 Redis
     */
    String generateProvisionToken(String productKey, String deviceName, String homeId, String authorizationHeader);

    /**
     * 设备端发起配网请求，通过 Token 换取真实密钥
     */
    ProvisionResp provisionDevice(ProvisionReq req);
}
