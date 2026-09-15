package com.aiot.device.service;

import com.aiot.device.dto.AdminAiEvalReportResp;

import java.util.List;
import java.util.Map;

public interface AdminConsoleRemoteQueryFacade {
    List<Map<String, Object>> loadHomes(String authorizationHeader);

    List<Map<String, Object>> loadHomeMembers(String homeId, String authorizationHeader);

    Map<String, Object> loadOpsOverview();

    AdminAiEvalReportResp loadAiEvalRegressionGate(String sceneType);
}
