package com.aiot.device.controller;

import com.aiot.device.dto.FirmwarePackageCreateReq;
import com.aiot.device.dto.FirmwarePackageResp;
import com.aiot.device.dto.OtaUpgradeTaskCreateReq;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.security.HomePermissionService;
import com.aiot.device.service.OtaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/ota")
public class OtaController {

    private final OtaService otaService;
    private final HomePermissionService homePermissionService;

    public OtaController(OtaService otaService, HomePermissionService homePermissionService) {
        this.otaService = otaService;
        this.homePermissionService = homePermissionService;
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
    public String createTask(@Valid @RequestBody OtaUpgradeTaskCreateReq req,
                             @RequestHeader("Authorization") String authorizationHeader) {
        homePermissionService.requireHomePermission(req.getHomeId(), authorizationHeader, 2, "无权限在该家庭创建OTA任务");
        return otaService.createUpgradeTask(req);
    }

    @GetMapping("/tasks/{taskId}")
    public OtaUpgradeTaskResp getTask(@PathVariable @NotBlank(message = "taskId 不能为空") String taskId,
                                      @RequestHeader("Authorization") String authorizationHeader) {
        OtaUpgradeTaskResp task = otaService.getUpgradeTask(taskId);
        homePermissionService.requireHomePermission(task.getHomeId(), authorizationHeader, 3, "无权限查看该OTA任务");
        return task;
    }

    @GetMapping("/tasks")
    public List<OtaUpgradeTaskResp> listTasks(@RequestParam @NotBlank(message = "homeId 不能为空") String homeId,
                                              @RequestHeader("Authorization") String authorizationHeader) {
        homePermissionService.requireHomePermission(homeId, authorizationHeader, 3, "无权限查看该家庭OTA任务");
        return otaService.listUpgradeTasks(homeId);
    }
}
