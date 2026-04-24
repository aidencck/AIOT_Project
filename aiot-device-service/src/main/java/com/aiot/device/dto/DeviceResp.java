package com.aiot.device.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceResp {
    private String id;
    private String deviceName;
    private String productKey;
    private Integer status;
    private String homeId;
    private String roomId;
    private String gatewayId;
    private String firmwareVersion;
    private LocalDateTime lastHeartbeatTime;
    private String deviceSecret; // 仅在创建时返回
}
