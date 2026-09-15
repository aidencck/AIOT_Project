package com.aiot.device.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.AllArgsConstructor;
@Data
@AllArgsConstructor
public class CommandResp {
    @Schema(description = "消息 ID")
    private String messageId;
    @Schema(description = "命令状态")
    private String status;
}
