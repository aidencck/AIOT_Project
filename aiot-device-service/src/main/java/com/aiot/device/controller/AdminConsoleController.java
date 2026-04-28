package com.aiot.device.controller;

import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.AdminPageResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;
import com.aiot.device.service.AdminConsoleService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/admin-console")
public class AdminConsoleController {

    private final AdminConsoleService adminConsoleService;

    public AdminConsoleController(AdminConsoleService adminConsoleService) {
        this.adminConsoleService = adminConsoleService;
    }

    @GetMapping("/overview")
    public AdminConsoleOverviewResp overview(@RequestParam(required = false) String homeId,
                                             @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getOverview(homeId, authorizationHeader);
    }

    @GetMapping("/latest-closure")
    public AdminLatestClosureResp latestClosure(@RequestParam(required = false) String homeId,
                                                @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.getLatestClosure(homeId, authorizationHeader);
    }

    @GetMapping("/devices/page")
    public AdminPageResp<DeviceResp> pageDevices(@Valid AdminDevicePageReq req,
                                                 @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.pageDevices(req, authorizationHeader);
    }

    @GetMapping("/ota/tasks/page")
    public AdminPageResp<OtaUpgradeTaskResp> pageOtaTasks(@Valid AdminOtaTaskPageReq req,
                                                          @RequestHeader("Authorization") String authorizationHeader) {
        return adminConsoleService.pageOtaTasks(req, authorizationHeader);
    }
}
