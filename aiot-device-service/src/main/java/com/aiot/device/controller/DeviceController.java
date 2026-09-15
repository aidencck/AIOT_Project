package com.aiot.device.controller;

import com.aiot.common.api.ResultCode;
import com.aiot.common.exception.BusinessException;
import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.dto.DevicePageReq;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceReq;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.DeviceUpdateReq;
import com.aiot.device.service.DeviceService;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "设备", description = "设备管理相关接口")
@RestController
@RequestMapping("/api/v1/devices")
@Validated
public class DeviceController {

    @Autowired
    private DeviceService deviceService;

    @Operation(summary = "创建设备", description = "在指定家庭下创建新设备")
    @ApiResponse(responseCode = "201", description = "创建成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping
    @RequireHomePermission(minRole = 2, denyMessage = "无权限在该家庭创建设备")
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceResp createDevice(@Valid @RequestBody DeviceReq req) {
        return deviceService.createDevice(req);
    }

    @Operation(summary = "获取设备详情", description = "根据设备 ID 查询设备详情")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @GetMapping("/{deviceId}")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限访问该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public DeviceResp getDevice(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId) {
        return deviceService.getDeviceById(deviceId);
    }

    @Operation(summary = "按家庭查询设备列表", description = "根据家庭 ID 查询设备列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭设备")
    public List<DeviceResp> listDevicesByHomeId(@Parameter(description = "家庭 ID") @RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        return deviceService.listDevicesByHomeId(homeId);
    }

    @Operation(summary = "分页查询设备", description = "分页查询指定家庭下的设备列表")
    @ApiResponse(responseCode = "200", description = "成功",
            content = @Content(schema = @Schema(implementation = DevicePageResp.class)))
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @GetMapping("/page")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭设备")
    public DevicePageResp pageDevices(@Valid DevicePageReq req) {
        if (!StringUtils.hasText(req.getHomeId())) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED, "分页查询必须传入 homeId");
        }
        return deviceService.pageDevices(req);
    }

    @Operation(summary = "更新设备信息", description = "根据设备 ID 更新设备信息")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @PutMapping("/{deviceId}")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限修改该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void updateDevice(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                             @Valid @RequestBody DeviceUpdateReq req) {
        deviceService.updateDevice(deviceId, req);
        return null;
    }

    @Operation(summary = "删除设备", description = "根据设备 ID 删除设备")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "设备不存在")
    @DeleteMapping("/{deviceId}")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限删除该设备", resourceType = ResourceType.DEVICE, resourceIdParam = "deviceId")
    public Void deleteDevice(@Parameter(description = "设备 ID") @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId) {
        deviceService.deleteDevice(deviceId);
        return null;
    }
}
