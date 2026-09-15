package com.aiot.device.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.util.StringUtils;

@Data
public class ProvisionReq {
    @Schema(description = "产品 Key")
    @NotBlank(message = "productKey 不能为空")
    @Size(max = 64, message = "productKey 长度不能超过64")
    private String productKey;

    @Schema(description = "设备名称")
    @Size(max = 64, message = "deviceName 长度不能超过64")
    private String deviceName;
    @Schema(description = "全局设备 ID")
    @Size(max = 64, message = "globalDeviceId 长度不能超过64")
    private String globalDeviceId;
    @Schema(description = "设备序列号")
    @Size(max = 64, message = "deviceSn 长度不能超过64")
    private String deviceSn;
    @Schema(description = "鉴权标识")
    @Size(max = 64, message = "authIdentity 长度不能超过64")
    private String authIdentity;
    @Schema(description = "配网 Token")
    @NotBlank(message = "provisionToken 不能为空")
    @Size(max = 128, message = "provisionToken 长度不能超过128")
    private String provisionToken;

    @AssertTrue(message = "deviceSn 或 deviceName 至少传一个")
    public boolean isDeviceIdentityPresent() {
        return StringUtils.hasText(deviceSn) || StringUtils.hasText(deviceName);
    }
}
