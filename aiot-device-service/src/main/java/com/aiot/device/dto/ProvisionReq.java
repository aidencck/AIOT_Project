package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProvisionReq {
    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;
    @NotBlank(message = "deviceName 不能为空")
    @Size(max = 64, message = "deviceName 长度不能超过64")
    private String deviceName;
    @NotBlank(message = "provisionToken 不能为空")
    @Size(max = 128, message = "provisionToken 长度不能超过128")
    private String provisionToken;
}
