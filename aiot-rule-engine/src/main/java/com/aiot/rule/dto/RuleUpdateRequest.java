package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RuleUpdateRequest {
    @NotBlank(message = "conditionEventType 不能为空")
    @Size(max = 64, message = "conditionEventType 长度不能超过64")
    private String conditionEventType;

    @Size(max = 64, message = "conditionDeviceId 长度不能超过64")
    private String conditionDeviceId;

    @NotBlank(message = "actionType 不能为空")
    @Size(max = 64, message = "actionType 长度不能超过64")
    private String actionType;

    @Size(max = 1024, message = "actionPayload 长度不能超过1024")
    private String actionPayload;
}
