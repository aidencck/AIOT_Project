package com.aiot.device.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OtaUpgradeRecordResp {
    private String recordId;
    private String deviceId;
    private String fromVersion;
    private String toVersion;
    private Integer status;
    private String errorMessage;
    private LocalDateTime reportTime;
}
