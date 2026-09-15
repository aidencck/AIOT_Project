package com.aiot.common.dto.device;

import lombok.Data;

@Data
public class DeviceStatusSummaryResp {
    private Long onlineCount;
    private Long offlineCount;
    private Long totalCount;
}
