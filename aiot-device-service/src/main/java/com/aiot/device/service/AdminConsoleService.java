package com.aiot.device.service;

import com.aiot.device.dto.AdminAiEvalReportResp;
import com.aiot.device.dto.AdminAiBusinessLiveFlowResp;
import com.aiot.device.dto.AdminWorkbenchResp;
import com.aiot.common.dto.ai.AiPersistenceAdminQueryResp;
import com.aiot.common.dto.ai.AiPersistenceHistoryDetailResp;
import com.aiot.device.dto.AdminDevicePageReq;
import com.aiot.device.dto.AdminOtaTaskPageReq;
import com.aiot.device.dto.AdminConsoleOverviewResp;
import com.aiot.device.dto.AdminLatestClosureResp;
import com.aiot.device.dto.DevicePageResp;
import com.aiot.device.dto.DeviceResp;
import com.aiot.device.dto.OtaTaskPageResp;
import com.aiot.device.dto.OtaUpgradeTaskResp;

import java.util.List;

public interface AdminConsoleService {
    AdminWorkbenchResp getWorkbench(String homeId, String authorizationHeader);

    AdminConsoleOverviewResp getOverview(String homeId, String authorizationHeader);

    AdminLatestClosureResp getLatestClosure(String homeId, String authorizationHeader);

    DevicePageResp pageDevices(AdminDevicePageReq req, String authorizationHeader);

    OtaTaskPageResp pageOtaTasks(AdminOtaTaskPageReq req, String authorizationHeader);

    List<AdminAiEvalReportResp> listAiEvalReports(List<String> sceneTypes, String authorizationHeader);

    AdminAiBusinessLiveFlowResp getAiBusinessLiveFlow(String authorizationHeader);

    AiPersistenceAdminQueryResp getAiPersistenceQuery(String authorizationHeader);

    AiPersistenceHistoryDetailResp getAiPersistenceHistoryDetail(String reportType, Long occurredAt, String authorizationHeader);
}
