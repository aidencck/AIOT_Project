package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkOrderResolveRequest {
    @NotBlank(message = "operator 不能为空")
    @Size(max = 64, message = "operator 长度不能超过64")
    private String operator;

    @NotBlank(message = "result 不能为空")
    @Size(max = 256, message = "result 长度不能超过256")
    private String result;
}
