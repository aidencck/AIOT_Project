package com.aiot.device.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceUpdateReq {
    @Size(max = 64, message = "deviceName 长度不能超过64")
    private String deviceName;
    @Size(max = 64, message = "roomId 长度不能超过64")
    private String roomId;
    @Size(max = 64, message = "gatewayId 长度不能超过64")
    private String gatewayId;
    @Size(max = 64, message = "firmwareVersion 长度不能超过64")
    private String firmwareVersion;
}
