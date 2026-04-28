package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DeviceReq {
    @NotBlank(message = "deviceName 不能为空")
    @Size(max = 64, message = "deviceName 长度不能超过64")
    private String deviceName;
    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;
    @NotBlank(message = "homeId 不能为空")
    @Size(max = 64, message = "homeId 长度不能超过64")
    private String homeId;
    @Size(max = 64, message = "roomId 长度不能超过64")
    private String roomId;
    @Size(max = 64, message = "gatewayId 长度不能超过64")
    private String gatewayId;
    @Size(max = 64, message = "firmwareVersion 长度不能超过64")
    private String firmwareVersion;
}
