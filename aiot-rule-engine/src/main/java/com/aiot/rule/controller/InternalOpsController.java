package com.aiot.rule.controller;

import com.aiot.common.api.Result;
import com.aiot.rule.service.OpsClosureService;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Hidden
@RestController
@RequestMapping("/api/v1/internal/ops/devices")
public class InternalOpsController {

    private final OpsClosureService opsClosureService;

    public InternalOpsController(OpsClosureService opsClosureService) {
        this.opsClosureService = opsClosureService;
    }

    @GetMapping("/{deviceIdentity}/summary")
    public Result<Map<String, Object>> getDeviceOpsSummary(@PathVariable String deviceIdentity) {
        return Result.success(opsClosureService.getDeviceOpsSummary(deviceIdentity));
    }
}
