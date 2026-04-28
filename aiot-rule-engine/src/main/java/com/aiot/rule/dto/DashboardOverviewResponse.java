package com.aiot.rule.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardOverviewResponse {
    private Integer onlineDeviceCount;
    private Integer offlineDeviceCount;
    private Integer todayAlarmCount;
    private Integer pendingWorkOrderCount;
    private Double oneTimeResolveRate;
}
