package com.aiot.auth.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmqxWebhookReq {
    @Schema(description = "事件动作", example = "client.connected")
    @NotBlank(message = "action 不能为空")
    @Size(max = 64, message = "action 长度不能超过64")
    private String action;
    @Schema(description = "客户端ID", example = "device_001")
    @NotBlank(message = "clientid 不能为空")
    @Size(max = 128, message = "clientid 长度不能超过128")
    private String clientid;
    @Schema(description = "用户名", example = "device_user")
    @NotBlank(message = "username 不能为空")
    @Size(max = 128, message = "username 长度不能超过128")
    private String username;
    @Schema(description = "时间戳（秒）", example = "1690000000")
    @NotNull(message = "timestamp 不能为空")
    private Long timestamp;
}
