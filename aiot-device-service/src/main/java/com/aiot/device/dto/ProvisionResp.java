package com.aiot.device.dto;

import lombok.Data;

@Data
public class ProvisionResp {
    private String deviceId;
    private String deviceSecret;
    private String mqttHost;
    private Integer mqttPort;
}
