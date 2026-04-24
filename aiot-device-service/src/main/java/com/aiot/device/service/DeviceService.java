package com.aiot.device.service;

import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

public interface DeviceService {
    DeviceResp createDevice(DeviceReq req);
    DeviceResp getDeviceById(String deviceId);
    List<DeviceResp> listDevicesByHomeId(String homeId);
    IPage<DeviceResp> pageDevices(DevicePageReq req);
    void updateDevice(String deviceId, DeviceUpdateReq req);
    void deleteDevice(String deviceId);
    void updateDeviceStatus(String deviceId, Integer status);
    void touchHeartbeat(String deviceId);
    void unbindDevicesByHomeId(String homeId);
    void unbindDevicesByRoomId(String roomId);
}
