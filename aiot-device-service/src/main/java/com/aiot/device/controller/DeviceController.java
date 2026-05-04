package com.aiot.device.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;
import com.aiot.device.service.DeviceService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 设备接口
 */
@RestController
@RequestMapping("/api/v1/devices")
@Validated
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @PostMapping
    @RequireHomePermission(minRole = 2, denyMessage = "无权限在该家庭创建设备")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResp createDevice(@Valid @RequestBody DeviceReq req) {
        return deviceService.createDevice(req);
    }

    @GetMapping("/{deviceId}")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限访问该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public DeviceResp getDevice(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId) {
        return deviceService.getDeviceById(deviceId);
    }

    @GetMapping
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭设备")
    public List<DeviceResp> listDevicesByHomeId(@RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        return deviceService.listDevicesByHomeId(homeId);
    }

    @GetMapping("/page")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭设备")
    public IPage<DeviceResp> pageDevices(@Valid DevicePageReq req) {
        if (!StringUtils.hasText(req.getHomeId())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "分页查询必须传入 homeId");
        }
        return deviceService.pageDevices(req);
    }

    @PutMapping("/{deviceId}")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限修改该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void updateDevice(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                             @Valid @RequestBody DeviceUpdateReq req) {
        deviceService.updateDevice(deviceId, req);
        return null;
    }

    @DeleteMapping("/{deviceId}")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限删除该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void deleteDevice(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId) {
        deviceService.deleteDevice(deviceId);
        return null;
    }
}
