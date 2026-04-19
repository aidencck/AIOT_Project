package com.aiot.auth.service;

import com.aiot.auth.dto.EmqxAuthReq;
import com.aiot.auth.dto.EmqxWebhookReq;

public interface AuthService {
    boolean authenticateDevice(EmqxAuthReq req);
    void handleDeviceStatusWebhook(EmqxWebhookReq req);
}
