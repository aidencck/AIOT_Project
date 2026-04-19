package com.aiot.device.service;

import com.aiot.device.dto.*;

public interface DeviceService {
    String createProduct(ProductReq req);
    DeviceResp registerDevice(DeviceReq req);
    DeviceStatusResp getDeviceStatus(String deviceId);
    CommandResp sendCommand(String deviceId, CommandReq req);
    Object getTelemetry(String deviceId);
}
