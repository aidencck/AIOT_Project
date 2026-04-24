package com.aiot.device.service;

import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.AdminPageResp;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;

public interface AdminConsoleService {
    AdminConsoleOverviewResp getOverview(String homeId, String authorizationHeader);

    AdminLatestClosureResp getLatestClosure(String homeId, String authorizationHeader);

    AdminPageResp<DeviceResp> pageDevices(AdminDevicePageReq req, String authorizationHeader);

    AdminPageResp<OtaUpgradeTaskResp> pageOtaTasks(AdminOtaTaskPageReq req, String authorizationHeader);
}
