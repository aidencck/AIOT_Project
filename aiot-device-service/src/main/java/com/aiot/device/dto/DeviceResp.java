package com.aiot.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeviceResp {
    private String id;
    private String globalDeviceId;
    private String deviceName;
    private String productKey;
    private String deviceSn;
    private String authIdentity;
    private Integer status;
    private String homeId;
    private String roomId;
    private String gatewayId;
    private String firmwareVersion;
    private LocalDateTime lastHeartbeatTime;
    private String deviceSecret; // 仅在创建时返回

    @JsonProperty("deviceId")
    public String getDeviceId() {
        return id;
    }
}
