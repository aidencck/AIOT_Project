package com.aiot.device.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class OtaUpgradeTaskResp {
    private String taskId;
    private String homeId;
    private String productKey;
    private String packageId;
    private String targetVersion;
    private Integer status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private LocalDateTime createTime;
    private List<OtaUpgradeRecordResp> records;
}
