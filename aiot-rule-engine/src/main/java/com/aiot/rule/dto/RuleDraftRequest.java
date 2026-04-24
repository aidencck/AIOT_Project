package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RuleDraftRequest {
    @NotBlank(message = "requirement 不能为空")
    @Size(max = 512, message = "requirement 长度不能超过512")
    private String requirement;
    @Size(max = 64, message = "deviceId 长度不能超过64")
    private String deviceId;
    @Size(max = 64, message = "eventType 长度不能超过64")
    private String eventType;
    @Size(max = 64, message = "actionType 长度不能超过64")
    private String actionType;
    @Size(max = 1024, message = "actionPayload 长度不能超过1024")
    private String actionPayload;
}
