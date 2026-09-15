package com.aiot.device.controller;

import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.service.OtaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "OTA升级", description = "OTA 固件升级相关接口")
@RestController
@Validated
@RequestMapping("/api/v1/ota")
public class OtaController {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @Operation(summary = "创建固件包", description = "创建 OTA 固件包")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/firmware-packages")
    public String createFirmwarePackage(@Valid @RequestBody FirmwarePackageCreateReq req) {
        return otaService.createFirmwarePackage(req);
    }

    @Operation(summary = "查询固件包列表", description = "查询固件包列表，可按产品 Key 过滤")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/firmware-packages")
    public List<FirmwarePackageResp> listFirmwarePackages(@Parameter(description = "产品 Key，可选") @RequestParam(required = false) String productKey) {
        return otaService.listFirmwarePackages(productKey);
    }

    @Operation(summary = "删除固件包", description = "根据固件包 ID 删除固件包")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "固件包不存在")
    @DeleteMapping("/firmware-packages/{packageId}")
    public Void deleteFirmwarePackage(@Parameter(description = "固件包 ID") @PathVariable @NotBlank(message = "packageId 不能为空") String packageId) {
        otaService.deleteFirmwarePackage(packageId);
        return null;
    }

    @Operation(summary = "创建 OTA 升级任务", description = "在指定家庭下创建 OTA 升级任务")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "400", description = "参数校验失败")
    @PostMapping("/tasks")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限在该家庭创建OTA任务")
    public String createTask(@Valid @RequestBody OtaUpgradeTaskCreateReq req) {
        return otaService.createUpgradeTask(req);
    }

    @Operation(summary = "获取 OTA 任务详情", description = "根据任务 ID 查询 OTA 任务详情")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    @GetMapping("/tasks/{taskId}")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该OTA任务", resourceType = ResourceType.OTA_TASK, resourceIdParam = "taskId")
    public OtaUpgradeTaskResp getTask(@Parameter(description = "任务 ID") @PathVariable @NotBlank(message = "taskId 不能为空") String taskId) {
        return otaService.getUpgradeTask(taskId);
    }

    @Operation(summary = "取消 OTA 升级任务", description = "根据任务 ID 取消进行中的 OTA 升级任务")
    @ApiResponse(responseCode = "200", description = "成功")
    @ApiResponse(responseCode = "404", description = "任务不存在")
    @PostMapping("/tasks/{taskId}/cancel")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限取消该OTA任务", resourceType = ResourceType.OTA_TASK, resourceIdParam = "taskId")
    public Void cancelTask(@Parameter(description = "任务 ID") @PathVariable @NotBlank(message = "taskId 不能为空") String taskId) {
        otaService.cancelUpgradeTask(taskId);
        return null;
    }

    @Operation(summary = "查询家庭 OTA 任务列表", description = "根据家庭 ID 查询 OTA 任务列表")
    @ApiResponse(responseCode = "200", description = "成功")
    @GetMapping("/tasks")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭OTA任务")
    public List<OtaUpgradeTaskResp> listTasks(@Parameter(description = "家庭 ID") @RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        return otaService.listUpgradeTasks(homeId);
    }
}
