package com.aiot.device.service;

import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;

public interface AdminConsoleService {
    AdminConsoleOverviewResp getOverview(String homeId, String authorizationHeader);

    AdminLatestClosureResp getLatestClosure(String homeId, String authorizationHeader);
}
