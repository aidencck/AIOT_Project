package com.aiot.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OtaUpgradeReportReq {

    @NotNull(message = "status 不能为空")
    @Min(value = 2, message = "status 仅支持成功或失败")
    @Max(value = 3, message = "status 仅支持成功或失败")
    private Integer status;

    @Size(max = 255, message = "errorMessage 长度不能超过255")
    private String errorMessage;

    @NotBlank(message = "fromVersion 不能为空")
    @Size(max = 64, message = "fromVersion 长度不能超过64")
    private String fromVersion;

    @NotBlank(message = "toVersion 不能为空")
    @Size(max = 64, message = "toVersion 长度不能超过64")
    private String toVersion;
}
