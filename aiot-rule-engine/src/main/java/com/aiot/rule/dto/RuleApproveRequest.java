package com.aiot.rule.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
/**
 * 规则审批请求DTO
 * 使用场景：
 * 1. 规则审核接口中，提交审批意见时传递审批人信息和审批备注
 * 2. 在RuleController的approve方法中接收前端传入的审批参数
 * 3. 配合参数校验框架，确保审批人必填且长度合规
 */
public class RuleApproveRequest {
    @NotBlank(message = "approver 不能为空")
    @Size(max = 64, message = "approver 长度不能超过64")
    private String approver;
    @Size(max = 255, message = "comment 长度不能超过255")
    private String comment;
}
