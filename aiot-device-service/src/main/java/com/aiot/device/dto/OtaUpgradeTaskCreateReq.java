package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class OtaUpgradeTaskCreateReq {

    @NotBlank(message = "homeId 不能为空")
    @Size(max = 64, message = "homeId 长度不能超过64")
    private String homeId;

    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    @NotBlank(message = "packageId 不能为空")
    @Size(max = 64, message = "packageId 长度不能超过64")
    private String packageId;

    @NotEmpty(message = "deviceIds 不能为空")
    private List<String> deviceIds;
}
