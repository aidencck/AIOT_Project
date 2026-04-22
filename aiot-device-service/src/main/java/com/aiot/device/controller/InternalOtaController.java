package com.aiot.device.controller;

import com.aiot.device.dto.OtaUpgradeReportReq;
import com.aiot.device.service.OtaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/internal/ota")
public class InternalOtaController {

    private final OtaService otaService;

    public InternalOtaController(OtaService otaService) {
        this.otaService = otaService;
    }

    @PostMapping("/tasks/{taskId}/devices/{deviceId}/report")
    public Void reportResult(@PathVariable @NotBlank(message = "taskId 不能为空") String taskId,
                             @PathVariable @NotBlank(message = "deviceId 不能为空") String deviceId,
                             @Valid @RequestBody OtaUpgradeReportReq req) {
        otaService.reportUpgradeResult(taskId, deviceId, req);
        return null;
    }
}
