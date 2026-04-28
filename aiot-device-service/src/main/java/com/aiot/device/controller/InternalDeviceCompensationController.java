package com.aiot.device.controller;

import com.aiot.common.api.Result;
import com.aiot.device.service.DeviceService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Home 域删除操作触发的设备补偿接口
 */
@RestController
@RequestMapping("/api/v1/internal/devices/unbind")
@Validated
public class InternalDeviceCompensationController {

    @Autowired
    private DeviceService deviceService;

    @PostMapping("/home/{homeId}")
    public Result<Boolean> unbindByHome(@PathVariable @NotBlank(message = "homeId 不能为空") String homeId) {
        deviceService.unbindDevicesByHomeId(homeId);
        return Result.success(Boolean.TRUE);
    }

    @PostMapping("/room/{roomId}")
    public Result<Boolean> unbindByRoom(@PathVariable @NotBlank(message = "roomId 不能为空") String roomId) {
        deviceService.unbindDevicesByRoomId(roomId);
        return Result.success(Boolean.TRUE);
    }
}
