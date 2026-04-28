package com.aiot.device.controller;

import com.aiot.device.dto.DeviceResp;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.DeviceService;
import com.aiot.device.service.DeviceShadowService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备影子接口
 */
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/shadow")
@Validated
public class DeviceShadowController {

    @Autowired
    private DeviceShadowService shadowService;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private HomePermissionService homePermissionService;

    @GetMapping
    public Map<String, Object> getShadow(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                                         @RequestHeader("Authorization") String authorizationHeader) {
        DeviceResp resp = deviceService.getDeviceById(deviceId);
        homePermissionService.requireHomePermission(resp.getHomeId(), authorizationHeader, 3, "无权限查看设备影子");
        return shadowService.getDeviceShadow(deviceId);
    }

    @PostMapping("/desired")
    public Void updateDesired(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                              @RequestBody @NotEmpty(message = "desired 不能为空") Map<String, Object> desired,
                              @RequestHeader("Authorization") String authorizationHeader,
                              @RequestParam(value = "expectedVersion", required = false) Long expectedVersion) {
        DeviceResp resp = deviceService.getDeviceById(deviceId);
        homePermissionService.requireHomePermission(resp.getHomeId(), authorizationHeader, 2, "无权限修改设备影子");
        shadowService.updateDesiredShadow(deviceId, desired, expectedVersion);
        return null;
    }

    @PostMapping("/reported")
    public Void updateReported(@PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                               @RequestBody @NotEmpty(message = "reported 不能为空") Map<String, Object> reported,
                               @RequestHeader("Authorization") String authorizationHeader,
                               @RequestParam(value = "expectedVersion", required = false) Long expectedVersion) {
        DeviceResp resp = deviceService.getDeviceById(deviceId);
        homePermissionService.requireHomePermission(resp.getHomeId(), authorizationHeader, 2, "无权限上报设备影子");
        shadowService.updateReportedShadow(deviceId, reported, expectedVersion);
        return null;
    }
}
