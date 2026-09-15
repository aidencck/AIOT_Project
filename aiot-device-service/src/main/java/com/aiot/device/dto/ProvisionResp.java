package com.aiot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class ProvisionResp {
    @Schema(description = "设备 ID")
    private String deviceId;
    @Schema(description = "全局设备 ID")
    private String globalDeviceId;
    @Schema(description = "设备序列号")
    private String deviceSn;
    @Schema(description = "鉴权标识")
    private String authIdentity;
    @Schema(description = "设备密钥")
    private String deviceSecret;
    @Schema(description = "MQTT 接入点主机")
    private String mqttHost;
    @Schema(description = "MQTT 接入点端口")
    private Integer mqttPort;
}
