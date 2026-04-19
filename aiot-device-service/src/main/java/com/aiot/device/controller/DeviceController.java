package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.device.dto.*;
import com.aiot.device.service.DeviceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
    
    @Autowired
    private DeviceService deviceService;

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("Device Service MVP is running smoothly!");
    }

    @PostMapping
    public Result<DeviceResp> registerDevice(@RequestBody DeviceReq req) {
        try {
            return Result.success(deviceService.registerDevice(req));
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/{deviceId}/status")
    public Result<DeviceStatusResp> getDeviceStatus(@PathVariable String deviceId) {
        try {
            return Result.success(deviceService.getDeviceStatus(deviceId));
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    @PostMapping("/{deviceId}/commands")
    public Result<CommandResp> sendCommand(@PathVariable String deviceId, @RequestBody CommandReq req) {
        try {
            return Result.success(deviceService.sendCommand(deviceId, req));
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }

    @GetMapping("/{deviceId}/telemetry")
    public Result<Object> getTelemetry(@PathVariable String deviceId) {
        try {
            return Result.success(deviceService.getTelemetry(deviceId));
        } catch (Exception e) {
            return Result.fail(500, e.getMessage());
        }
    }
}
