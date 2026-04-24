package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.device.service.DeviceService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证服务回调设备状态同步入口（内部调用）
 */
@RestController
@RequestMapping("/api/v1/internal/devices")
@Validated
public class InternalDeviceStatusController {

    @Autowired
    private DeviceService deviceService;

    @PostMapping("/{deviceId}/status")
    public Result<Boolean> syncStatus(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                                      @RequestParam @Min(value = 1, message = "status 最小值为1")
                                      @Max(value = 2, message = "status 最大值为2") Integer status) {
        deviceService.updateDeviceStatus(deviceId, status);
        return Result.success(Boolean.TRUE);
    }
}
