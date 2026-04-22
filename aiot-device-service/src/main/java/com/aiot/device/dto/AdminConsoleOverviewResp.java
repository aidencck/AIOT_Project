package com.aiot.device.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminConsoleOverviewResp {
    private Integer homeCount;
    private Integer memberCount;
    private Integer productCount;
    private Integer deviceCount;
    private Integer otaTaskCount;
    private Integer todayAlarmCount;
    private Integer pendingWorkOrderCount;
    private Double oneTimeResolveRate;
}
