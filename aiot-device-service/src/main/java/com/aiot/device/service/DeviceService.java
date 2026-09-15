package com.aiot.device.service;

import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;

import java.util.List;

public interface DeviceService {
    DeviceResp createDevice(DeviceReq req);
    DeviceResp claimUnboundDevice(DeviceReq req);
    DeviceResp getDeviceById(String deviceId);
    DeviceStatusSummaryResp getStatusSummary();
    List<DeviceResp> listDevicesByHomeId(String homeId);
    DevicePageResp pageDevices(DevicePageReq req);
    void updateDevice(String deviceId, DeviceUpdateReq req);
    void deleteDevice(String deviceId);
    void updateDeviceStatus(String deviceId, Integer status);
    void touchHeartbeat(String deviceId);
    void unbindDevicesByHomeId(String homeId);
    void unbindDevicesByRoomId(String roomId);
}
