package com.aiot.device.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AdminWorkbenchResp {
    private String selectedHomeId;
    private Integer closureScore;
    private List<Map<String, Object>> homes;
    private List<Map<String, Object>> members;
    private List<ProductResp> products;
    private List<DeviceResp> devices;
    private List<OtaUpgradeTaskResp> otaTasks;
    private Map<String, Object> opsOverview;
    private List<AdminAiEvalReportResp> aiEvalReports;
    private List<AdminClosureStageResp> closureStages;
    private AdminConsoleOverviewResp overview;
}
