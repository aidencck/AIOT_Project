package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProvisionTokenReq {
    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    @NotBlank(message = "deviceName 不能为空")
    @Size(max = 64, message = "deviceName 长度不能超过64")
    private String deviceName;

    @NotBlank(message = "homeId 不能为空")
    @Size(max = 64, message = "homeId 长度不能超过64")
    private String homeId;
}
