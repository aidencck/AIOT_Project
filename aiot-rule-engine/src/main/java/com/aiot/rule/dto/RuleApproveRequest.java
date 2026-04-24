package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RuleApproveRequest {
    @NotBlank(message = "approver 不能为空")
    @Size(max = 64, message = "approver 长度不能超过64")
    private String approver;
    @Size(max = 255, message = "comment 长度不能超过255")
    private String comment;
}
