package com.aiot.device.controller;

import com.aiot.device.annotation.RequireHomePermission;
import com.aiot.device.annotation.ResourceType;
import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.service.OtaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/ota")
public class OtaController {

    private final OtaService otaService;

    public OtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @PostMapping("/firmware-packages")
    public String createFirmwarePackage(@Valid @RequestBody FirmwarePackageCreateReq req) {
        return otaService.createFirmwarePackage(req);
    }

    @GetMapping("/firmware-packages")
    public List<FirmwarePackageResp> listFirmwarePackages(@RequestParam(required = false) String productKey) {
        return otaService.listFirmwarePackages(productKey);
    }

    @PostMapping("/tasks")
    @RequireHomePermission(minRole = 2, denyMessage = "无权限在该家庭创建OTA任务")
    public String createTask(@Valid @RequestBody OtaUpgradeTaskCreateReq req) {
        return otaService.createUpgradeTask(req);
    }

    @GetMapping("/tasks/{taskId}")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该OTA任务", resourceType = ResourceType.OTA_TASK, resourceIdParam = "taskId")
    public OtaUpgradeTaskResp getTask(@PathVariable @NotBlank(message = "taskId 不能为空") String taskId) {
        return otaService.getUpgradeTask(taskId);
    }

    @GetMapping("/tasks")
    @RequireHomePermission(minRole = 3, denyMessage = "无权限查看该家庭OTA任务")
    public List<OtaUpgradeTaskResp> listTasks(@RequestParam @NotBlank(message = "homeId 不能为空") String homeId) {
        return otaService.listUpgradeTasks(homeId);
    }
}
