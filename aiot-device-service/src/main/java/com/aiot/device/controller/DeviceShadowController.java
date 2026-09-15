package com.aiot.device.controller;

import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.service.DeviceShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 设备影子接口
 */
@Tag(name = "设备影子", description = "设备影子读写相关接口")
@RestController
@RequestMapping("/api/v1/devices/{deviceId}/shadow")
@Validated
public class DeviceShadowController {

    @Autowired
    private DeviceShadowService shadowService;

    @Operation(summary = "获取设备影子", description = "获取指定设备的影子数据")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @GetMapping
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看设备影子", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Map<String, Object> getShadow(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId) {
        return shadowService.getDeviceShadow(deviceId);
    }

    @Operation(summary = "更新期望状态", description = "更新设备影子的期望状态")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @PostMapping("/desired")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限修改设备影子", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void updateDesired(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                              @RequestBody @NotEmpty(message = "desired 不能为空") Map<String, Object> desired,
                              @Parameter(description = "期望版本号，可选") @RequestParam(value = "expectedVersion", required = false) Long expectedVersion) {
        shadowService.updateDesiredShadow(deviceId, desired, expectedVersion);
        return null;
    }

    @Operation(summary = "上报实际状态", description = "上报设备影子的实际状态")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @PostMapping("/reported")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限上报设备影子", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void updateReported(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                               @RequestBody @NotEmpty(message = "reported 不能为空") Map<String, Object> reported,
                               @Parameter(description = "期望版本号，可选") @RequestParam(value = "expectedVersion", required = false) Long expectedVersion) {
        shadowService.updateReportedShadow(deviceId, reported, expectedVersion);
        return null;
    }
}
