package com.aiot.auth.service;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;

public interface AuthService {
    boolean authenticateDevice(EmqxAuthReq req);
    boolean verifyWebhookSignature(String action, String clientId, String username, Long timestamp, String signatureHeader);
    void handleDeviceStatusWebhook(EmqxWebhookReq req);
}
