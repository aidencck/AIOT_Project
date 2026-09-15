package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.common.dto.device.DeviceStatusSummaryResp;
import com.aiot.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/v1/internal/devices/status")
public class InternalDeviceStatusSummaryController {

    @Autowired
    private DeviceService deviceService;

    @GetMapping("/summary")
    public Result<DeviceStatusSummaryResp> getStatusSummary() {
        return Result.success(deviceService.getStatusSummary());
    }
}
