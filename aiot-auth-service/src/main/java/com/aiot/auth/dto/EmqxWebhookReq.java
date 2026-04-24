package com.aiot.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmqxWebhookReq {
    @NotBlank(message = "action 不能为空")
    @Size(max = 64, message = "action 长度不能超过64")
    private String action;
    @NotBlank(message = "clientid 不能为空")
    @Size(max = 128, message = "clientid 长度不能超过128")
    private String clientid;
    @NotBlank(message = "username 不能为空")
    @Size(max = 128, message = "username 长度不能超过128")
    private String username;
    @NotNull(message = "timestamp 不能为空")
    private Long timestamp;
}
